package nan.produced.prism.core.assistant.infrastructure.springai;

import io.micrometer.observation.ObservationRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SpringAiAssistantChatLlmClient implements AssistantChatLlmClient {

    private final AssistantChatProperties properties;
    private final AssistantChatModelRouter router;
    private final AssistantSpringAiToolCallbacks toolCallbacks;
    private final AssistantToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final ToolCallingManager toolCallingManager;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private volatile String localVllmModelOverride;
    private volatile boolean localVllmToolCallingDisabled;

    public SpringAiAssistantChatLlmClient(AssistantChatProperties properties,
                                         AssistantChatModelRouter router,
                                         AssistantSpringAiToolCallbacks toolCallbacks,
                                         AssistantToolExecutor toolExecutor,
                                         ObjectMapper objectMapper,
                                         ToolCallingManager toolCallingManager,
                                         RetryTemplate retryTemplate,
                                         ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.router = router;
        this.toolCallbacks = toolCallbacks;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public StreamResult stream(UUID userId, List<Message> messages, StreamOptions streamOptions, ToolEventListener toolEvents, Consumer<String> onDelta) {
        AssistantChatModelRouter.LlmTarget target = router.resolveForUser(userId);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().baseUrl(target.baseUrl());
        // Spring AI OpenAiApi requires apiKey to be set even for OpenAI-compatible local servers.
        // For local-vllm we allow an arbitrary placeholder key.
        String apiKey = target.apiKey();
        if (!StringUtils.hasText(apiKey)) {
            apiKey = "local";
        }
        apiBuilder.apiKey(apiKey);
        OpenAiApi api = apiBuilder.build();

        String model = target.model();
        if ("local-vllm".equalsIgnoreCase(target.provider()) && StringUtils.hasText(localVllmModelOverride)) {
            model = localVllmModelOverride;
        }

        boolean toolCallingEnabled = !("local-vllm".equalsIgnoreCase(target.provider()) && localVllmToolCallingDisabled);
        OpenAiChatOptions.Builder chatOptionsBuilder = buildChatOptionsBuilder(userId, target, model, streamOptions, toolCallingEnabled);
        Integer maxCompletionTokens = streamOptions != null && streamOptions.maxCompletionTokens() > 0 ? streamOptions.maxCompletionTokens() : null;
        if (maxCompletionTokens != null) {
            // vLLM is OpenAI-compatible and typically supports max_tokens; OpenAI may prefer max_completion_tokens.
            chatOptionsBuilder.maxTokens(maxCompletionTokens);
            chatOptionsBuilder.maxCompletionTokens(maxCompletionTokens);
        }
        OpenAiChatOptions chatOptions = chatOptionsBuilder.build();

        OpenAiChatModel chatModel = new OpenAiChatModel(api, chatOptions, toolCallingManager, retryTemplate, observationRegistry);

        List<org.springframework.ai.chat.messages.Message> history = toSpringAiMessages(messages);

        // MVP tool loop:
        // - call() to get tool calls (if any)
        // - execute tools server-side with audit toolCallId
        // - append ToolResponseMessage
        // - repeat until no tool calls, then return final text (single delta)
        int toolRounds = 0;
        int configuredMaxRounds = properties.tools() != null ? properties.tools().maxRounds() : 3;
        int configuredMaxCalls = properties.tools() != null ? properties.tools().maxCallsPerRound() : 5;
        int maxToolRounds = streamOptions != null && streamOptions.maxToolRounds() > 0 ? streamOptions.maxToolRounds() : Math.max(1, configuredMaxRounds);
        int maxCallsPerRound = streamOptions != null && streamOptions.maxCallsPerRound() > 0 ? streamOptions.maxCallsPerRound() : Math.max(1, configuredMaxCalls);
        int maxToolResultChars = streamOptions != null ? streamOptions.maxToolResultChars() : 0;
        long promptTokensSum = 0;
        long completionTokensSum = 0;
        boolean modelFallbackAttempted = false;
        boolean toolFallbackAttempted = false;

        while (true) {
            ChatResponse response;
            try {
                response = chatModel.call(new Prompt(history, chatOptions));
            } catch (Exception ex) {
                if (!toolFallbackAttempted
                        && "local-vllm".equalsIgnoreCase(target.provider())
                        && !localVllmToolCallingDisabled
                        && isAutoToolChoiceNotSupported(ex)) {
                    // vLLM requires server-side flags for tool_choice=auto. Fall back to plain chat.
                    toolFallbackAttempted = true;
                    localVllmToolCallingDisabled = true;
                    chatOptionsBuilder = buildChatOptionsBuilder(userId, target, chatOptions.getModel(), streamOptions, false);
                    if (maxCompletionTokens != null) {
                        chatOptionsBuilder.maxTokens(maxCompletionTokens);
                        chatOptionsBuilder.maxCompletionTokens(maxCompletionTokens);
                    }
                    chatOptions = chatOptionsBuilder.build();
                    chatModel = new OpenAiChatModel(api, chatOptions, toolCallingManager, retryTemplate, observationRegistry);
                    continue;
                }
                if (!modelFallbackAttempted
                        && "local-vllm".equalsIgnoreCase(target.provider())
                        && isModelNotFound(ex)) {
                    String fallbackModel = resolveFirstModelId(target.baseUrl(), apiKey);
                    String configuredModel = target.model();
                    if (StringUtils.hasText(fallbackModel)
                            && (!StringUtils.hasText(configuredModel) || !fallbackModel.equalsIgnoreCase(configuredModel))) {
                        modelFallbackAttempted = true;
                        localVllmModelOverride = fallbackModel;
                        chatOptionsBuilder.model(fallbackModel);
                        chatOptions = chatOptionsBuilder.build();
                        chatModel = new OpenAiChatModel(api, chatOptions, toolCallingManager, retryTemplate, observationRegistry);
                        continue;
                    }
                }
                throw ex;
            }
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return new StreamResult("error", null, null, null);
            }
            var usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
            if (usage != null) {
                if (usage.getPromptTokens() != null) {
                    promptTokensSum += usage.getPromptTokens();
                }
                if (usage.getCompletionTokens() != null) {
                    completionTokensSum += usage.getCompletionTokens();
                }
            }

            var assistant = response.getResult().getOutput();
            if (assistant.hasToolCalls()) {
                history.add(assistant);

                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                int callCount = 0;
                for (var call : assistant.getToolCalls()) {
                    callCount++;
                    if (callCount > maxCallsPerRound) {
                        String toolCallId = "tool-" + UUID.randomUUID();
                        if (toolEvents != null) {
                            toolEvents.onToolOutputError(toolCallId, "Too many tool calls in one request");
                        }
                        ObjectNode err = objectMapper.createObjectNode();
                        err.put("error", "too_many_tool_calls");
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, "toolLimit", toJsonString(err, maxToolResultChars)));
                        break;
                    }
                    String toolCallId = StringUtils.hasText(call.id()) ? call.id() : ("tool-" + UUID.randomUUID());
                    String toolName = call.name();
                    JsonNode inputNode = parseArgs(call.arguments());

                    if (toolEvents != null) {
                        toolEvents.onToolInputAvailable(toolCallId, toolName, inputNode);
                    }

                    var exec = toolExecutor.execute(userId, new AssistantToolCall(toolCallId, toolName, inputNode));
                    if (exec.success()) {
                        if (toolEvents != null) {
                            toolEvents.onToolOutputAvailable(toolCallId, exec.output());
                        }
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toJsonString(exec.output(), maxToolResultChars)));
                    } else {
                        if (toolEvents != null) {
                            toolEvents.onToolOutputError(toolCallId, exec.errorText());
                        }
                        ObjectNode err = objectMapper.createObjectNode();
                        err.put("error", exec.errorText() == null ? "Tool error" : exec.errorText());
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toJsonString(err, maxToolResultChars)));
                    }
                }
                history.add(new ToolResponseMessage(responses));

                toolRounds++;
                if (toolRounds >= maxToolRounds) {
                    onDelta.accept("工具调用轮次过多，已中止。请缩小问题范围或提供更具体的条件。");
                    Integer pt = safeIntOrNull(promptTokensSum);
                    Integer ct = safeIntOrNull(completionTokensSum);
                    Integer tt = safeIntOrNull(promptTokensSum + completionTokensSum);
                    return new StreamResult("error", pt, ct, tt);
                }
                continue;
            }

            String content = assistant.getText();

            // Fallback for models/servers that don't support structured tool calls but emit them in text:
            //   <tool_call> {"name":"xxx","arguments":{...}} </tool_call>
            List<AssistantToolCall> embeddedToolCalls = parseToolCallsFromText(content, maxCallsPerRound);
            if (embeddedToolCalls != null && !embeddedToolCalls.isEmpty()) {
                String stripped = stripToolCallBlocks(content);
                if (StringUtils.hasText(stripped)) {
                    history.add(new AssistantMessage(stripped));
                } else {
                    history.add(new AssistantMessage(""));
                }

                var toolResults = objectMapper.createArrayNode();
                int callCount = 0;
                for (AssistantToolCall call : embeddedToolCalls) {
                    callCount++;
                    if (callCount > maxCallsPerRound) {
                        String toolCallId = "tool-" + UUID.randomUUID();
                        if (toolEvents != null) {
                            toolEvents.onToolOutputError(toolCallId, "Too many tool calls in one request");
                        }
                        ObjectNode err = objectMapper.createObjectNode();
                        err.put("error", "too_many_tool_calls");
                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("toolCallId", toolCallId);
                        item.put("toolName", "toolLimit");
                        item.set("input", objectMapper.createObjectNode());
                        item.set("output", err);
                        toolResults.add(item);
                        break;
                    }

                    if (toolEvents != null) {
                        toolEvents.onToolInputAvailable(call.toolCallId(), call.toolName(), call.input());
                    }

                    var exec = toolExecutor.execute(userId, call);
                    if (exec.success()) {
                        if (toolEvents != null) {
                            toolEvents.onToolOutputAvailable(call.toolCallId(), exec.output());
                        }
                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("toolCallId", call.toolCallId());
                        item.put("toolName", call.toolName());
                        item.set("input", call.input());
                        item.set("output", exec.output());
                        toolResults.add(item);
                    } else {
                        if (toolEvents != null) {
                            toolEvents.onToolOutputError(call.toolCallId(), exec.errorText());
                        }
                        ObjectNode err = objectMapper.createObjectNode();
                        err.put("error", exec.errorText() == null ? "Tool error" : exec.errorText());
                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("toolCallId", call.toolCallId());
                        item.put("toolName", call.toolName());
                        item.set("input", call.input());
                        item.set("output", err);
                        toolResults.add(item);
                    }
                }

                // In text-embedded tool-call fallback mode, feed tool results back as plain messages for maximum
                // compatibility with OpenAI-compatible servers that don't implement tool role/tool_calls fully.
                history.add(new SystemMessage("""
                        以下是服务端执行的工具调用结果（JSON）：
                        %s

                        请基于这些结果继续回答用户；不要输出任何 <tool_call> 或 </tool_call> 标签。
                        """.formatted(toJsonString(toolResults, maxToolResultChars))));

                toolRounds++;
                if (toolRounds >= maxToolRounds) {
                    onDelta.accept("工具调用轮次过多，已中止。请缩小问题范围或提供更具体的条件。");
                    Integer pt = safeIntOrNull(promptTokensSum);
                    Integer ct = safeIntOrNull(completionTokensSum);
                    Integer tt = safeIntOrNull(promptTokensSum + completionTokensSum);
                    return new StreamResult("error", pt, ct, tt);
                }
                continue;
            }

            if (StringUtils.hasText(content)) {
                String cleaned = stripToolCallBlocks(content);
                if (StringUtils.hasText(cleaned)) {
                    onDelta.accept(cleaned);
                }
            }
            String finishReason = response.getResult().getMetadata() != null ? response.getResult().getMetadata().getFinishReason() : null;
            Integer pt = safeIntOrNull(promptTokensSum);
            Integer ct = safeIntOrNull(completionTokensSum);
            Integer tt = safeIntOrNull(promptTokensSum + completionTokensSum);
            return new StreamResult(finishReason, pt, ct, tt);
        }
    }

    private OpenAiChatOptions.Builder buildChatOptionsBuilder(UUID userId,
                                                              AssistantChatModelRouter.LlmTarget target,
                                                              String model,
                                                              StreamOptions streamOptions,
                                                              boolean toolCallingEnabled) {
        List<ToolCallback> callbacks = toolCallingEnabled ? toolCallbacks.buildAll() : List.of();
        // Tool execution is handled manually (we need toolCallId for audit + custom SSE tool events).
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(target.temperature())
                .toolContext(Map.of("userId", userId.toString()))
                .internalToolExecutionEnabled(false);

        if (toolCallingEnabled && callbacks != null && !callbacks.isEmpty()) {
            builder.toolCallbacks(callbacks)
                    .parallelToolCalls(true)
                    .toolChoice("auto");
        } else {
            builder.parallelToolCalls(false)
                    .toolChoice("none");
        }

        return builder;
    }

    private static boolean isModelNotFound(Exception ex) {
        if (ex == null) {
            return false;
        }
        String message = ex.getMessage();
        if (StringUtils.hasText(message) && message.contains("does not exist")) {
            return true;
        }
        Throwable cause = ex.getCause();
        if (cause == null || cause == ex) {
            return false;
        }
        if (cause instanceof Exception causeEx) {
            return isModelNotFound(causeEx);
        }
        return StringUtils.hasText(cause.getMessage()) && cause.getMessage().contains("does not exist");
    }

    private static boolean isAutoToolChoiceNotSupported(Exception ex) {
        if (ex == null) {
            return false;
        }
        String message = ex.getMessage();
        if (StringUtils.hasText(message)
                && (message.contains("--enable-auto-tool-choice")
                || message.contains("\"auto\" tool choice requires"))) {
            return true;
        }
        Throwable cause = ex.getCause();
        if (cause == null || cause == ex) {
            return false;
        }
        if (cause instanceof Exception causeEx) {
            return isAutoToolChoiceNotSupported(causeEx);
        }
        String causeMessage = cause.getMessage();
        return StringUtils.hasText(causeMessage)
                && (causeMessage.contains("--enable-auto-tool-choice")
                || causeMessage.contains("\"auto\" tool choice requires"));
    }

    private String resolveFirstModelId(String baseUrl, String apiKey) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        try {
            RestClient client = RestClient.builder().baseUrl(baseUrl).build();
            RestClient.RequestHeadersSpec<?> spec = client.get().uri("/v1/models");
            if (StringUtils.hasText(apiKey)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            String payload = spec.retrieve().body(String.class);
            if (!StringUtils.hasText(payload)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    String id = item != null && item.hasNonNull("id") ? item.get("id").asText() : null;
                    if (StringUtils.hasText(id)) {
                        return id.trim();
                    }
                }
            }
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Integer safeIntOrNull(long v) {
        if (v <= 0) {
            return null;
        }
        if (v > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) v;
    }

    private static List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(List<Message> messages) {
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (Message m : messages) {
            if (m == null || !StringUtils.hasText(m.role())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            String content = m.content() == null ? "" : m.content();
            switch (role) {
                case "system" -> result.add(new SystemMessage(content));
                case "user" -> result.add(new UserMessage(content));
                case "assistant" -> result.add(new AssistantMessage(content));
                default -> {
                    // Ignore unsupported roles for now.
                }
            }
        }
        return result;
    }

    private JsonNode parseArgs(String argsJson) {
        if (!StringUtils.hasText(argsJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(argsJson);
        } catch (Exception e) {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("_raw", argsJson);
            return out;
        }
    }

    private String toJsonString(JsonNode node, int maxChars) {
        try {
            String json = objectMapper.writeValueAsString(node);
            if (maxChars > 0 && json.length() > maxChars) {
                ObjectNode out = objectMapper.createObjectNode();
                out.put("_truncated", true);
                out.put("_maxChars", maxChars);
                out.put("_text", json.substring(0, maxChars));
                return objectMapper.writeValueAsString(out);
            }
            return json;
        } catch (Exception e) {
            return "{\"error\":\"failed_to_serialize\"}";
        }
    }

    List<AssistantToolCall> parseToolCallsFromText(String content, int maxCallsPerRound) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        int searchFrom = 0;
        List<AssistantToolCall> calls = new ArrayList<>();
        while (searchFrom < content.length()) {
            int start = content.indexOf("<tool_call>", searchFrom);
            if (start < 0) {
                break;
            }
            int end = content.indexOf("</tool_call>", start);
            if (end < 0) {
                break;
            }

            if (maxCallsPerRound > 0 && calls.size() >= maxCallsPerRound) {
                break;
            }

            String raw = content.substring(start + "<tool_call>".length(), end).trim();
            String json = stripMarkdownCodeFence(raw);
            try {
                JsonNode node = objectMapper.readTree(json);
                String toolName = node != null && node.hasNonNull("name") ? node.get("name").asText() : null;
                if (!StringUtils.hasText(toolName)) {
                    searchFrom = end + "</tool_call>".length();
                    continue;
                }
                JsonNode argsNode = node.has("arguments") ? node.get("arguments") : null;
                if (argsNode == null || argsNode.isNull()) {
                    argsNode = objectMapper.createObjectNode();
                } else if (argsNode.isTextual()) {
                    argsNode = parseArgs(argsNode.asText());
                }
                calls.add(new AssistantToolCall("tool-" + UUID.randomUUID(), toolName, argsNode));
            } catch (Exception ignore) {
                // ignore invalid embedded tool call block
            }

            searchFrom = end + "</tool_call>".length();
        }
        return calls;
    }

    static String stripToolCallBlocks(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String out = content;
        int guard = 0;
        while (guard++ < 50) {
            int start = out.indexOf("<tool_call>");
            if (start < 0) {
                break;
            }
            int end = out.indexOf("</tool_call>", start);
            if (end < 0) {
                break;
            }
            out = out.substring(0, start) + out.substring(end + "</tool_call>".length());
        }
        return out.trim();
    }

    static String stripMarkdownCodeFence(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNewline = t.indexOf('\n');
        if (firstNewline < 0) {
            return t;
        }
        int lastFence = t.lastIndexOf("```");
        if (lastFence <= firstNewline) {
            return t.substring(firstNewline + 1).trim();
        }
        return t.substring(firstNewline + 1, lastFence).trim();
    }
}
