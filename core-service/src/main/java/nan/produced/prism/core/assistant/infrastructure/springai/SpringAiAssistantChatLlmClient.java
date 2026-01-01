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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

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
    public StreamResult stream(UUID userId, List<Message> messages, ToolEventListener toolEvents, Consumer<String> onDelta) {
        AssistantChatModelRouter.LlmTarget target = router.resolveForUser(userId);

        OpenAiApi.Builder apiBuilder = OpenAiApi.builder().baseUrl(target.baseUrl());
        if (StringUtils.hasText(target.apiKey())) {
            apiBuilder.apiKey(target.apiKey());
        }
        OpenAiApi api = apiBuilder.build();

        List<ToolCallback> callbacks = toolCallbacks.buildAll();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(target.model())
                .temperature(target.temperature())
                .toolCallbacks(callbacks)
                .toolContext(Map.of("userId", userId.toString()))
                // Tool execution is handled manually (we need toolCallId for audit + custom SSE tool events).
                .internalToolExecutionEnabled(false)
                .parallelToolCalls(true)
                .toolChoice("auto")
                .build();

        OpenAiChatModel chatModel = new OpenAiChatModel(api, options, toolCallingManager, retryTemplate, observationRegistry);

        List<org.springframework.ai.chat.messages.Message> history = toSpringAiMessages(messages);

        // MVP tool loop:
        // - call() to get tool calls (if any)
        // - execute tools server-side with audit toolCallId
        // - append ToolResponseMessage
        // - repeat until no tool calls, then return final text (single delta)
        int toolRounds = 0;
        int maxToolRounds = properties.tools() != null ? Math.max(1, properties.tools().maxRounds()) : 3;
        int maxCallsPerRound = properties.tools() != null ? Math.max(1, properties.tools().maxCallsPerRound()) : 5;

        while (true) {
            ChatResponse response = chatModel.call(new Prompt(history, options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return new StreamResult("error");
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
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, "toolLimit", toJsonString(err)));
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
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toJsonString(exec.output())));
                    } else {
                        if (toolEvents != null) {
                            toolEvents.onToolOutputError(toolCallId, exec.errorText());
                        }
                        ObjectNode err = objectMapper.createObjectNode();
                        err.put("error", exec.errorText() == null ? "Tool error" : exec.errorText());
                        responses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toJsonString(err)));
                    }
                }
                history.add(new ToolResponseMessage(responses));

                toolRounds++;
                if (toolRounds >= maxToolRounds) {
                    onDelta.accept("工具调用轮次过多，已中止。请缩小问题范围或提供更具体的条件。");
                    return new StreamResult("error");
                }
                continue;
            }

            String content = assistant.getText();
            if (StringUtils.hasText(content)) {
                onDelta.accept(content);
            }
            String finishReason = response.getResult().getMetadata() != null ? response.getResult().getMetadata().getFinishReason() : null;
            return new StreamResult(finishReason);
        }
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

    private String toJsonString(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"failed_to_serialize\"}";
        }
    }
}
