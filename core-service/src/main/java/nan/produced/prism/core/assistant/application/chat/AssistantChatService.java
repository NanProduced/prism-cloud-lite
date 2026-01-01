package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final ObjectMapper objectMapper;
    private final AssistantChatProperties properties;
    private final AssistantSystemPromptTemplate systemPromptTemplate;
    private final AssistantNavigationToolResolver navigationToolResolver;
    private final AssistantRagContextService ragContextService;
    private final AssistantChatLlmClient llmClient;
    private final AssistantToolPlanner toolPlanner;
    private final AssistantToolExecutor toolExecutor;

    public void handle(UUID userId, JsonNode request, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> doHandle(userId, request, emitter));
    }

    private void doHandle(UUID userId, JsonNode request, SseEmitter emitter) {
        String userText = AiSdkChatRequestParser.lastUserText(request);
        if (userText == null || userText.isBlank()) {
            sendErrorAndDone(emitter, "Missing user message");
            return;
        }

        try {
            sendChunk(emitter, Map.of("type", "start"));

            AssistantNavigationToolResolver.NavigationTarget nav = navigationToolResolver.resolve(userText);
            if (nav != null) {
                String toolCallId = "tool-" + UUID.randomUUID();
                sendChunk(emitter, Map.of(
                        "type", "tool-input-available",
                        "toolCallId", toolCallId,
                        "toolName", "navigateToPage",
                        "input", Map.of("path", nav.path(), "label", nav.label()),
                        "providerExecuted", false
                ));
                sendChunk(emitter, Map.of("type", "finish", "finishReason", "tool-calls"));
                sendDone(emitter);
                return;
            }

            AssistantToolCall plannedToolCall = "spring-ai".equalsIgnoreCase(properties.engine())
                    ? null
                    : toolPlanner.plan(userText);
            Object toolResultForPrompt = null;
            if (!"spring-ai".equalsIgnoreCase(properties.engine()) && plannedToolCall != null) {
                sendChunk(emitter, Map.of(
                        "type", "tool-input-available",
                        "toolCallId", plannedToolCall.toolCallId(),
                        "toolName", plannedToolCall.toolName(),
                        "input", plannedToolCall.input(),
                        "providerExecuted", true
                ));
                var exec = toolExecutor.execute(userId, plannedToolCall);
                if (exec.success()) {
                    toolResultForPrompt = exec.output();
                    sendChunk(emitter, Map.of(
                            "type", "tool-output-available",
                            "toolCallId", plannedToolCall.toolCallId(),
                            "output", exec.output(),
                            "providerExecuted", true
                    ));
                } else {
                    sendChunk(emitter, Map.of(
                            "type", "tool-output-error",
                            "toolCallId", plannedToolCall.toolCallId(),
                            "errorText", exec.errorText(),
                            "providerExecuted", true
                    ));
                }
            }

            AssistantRagContextService.RagContext rag = properties.rag().enabled()
                    ? ragContextService.buildContext(userText)
                    : AssistantRagContextService.RagContext.empty();

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", buildSystemPrompt(rag, toolResultForPrompt)));
            messages.add(new Message("user", userText));

            String textId = "text-1";
            sendChunk(emitter, Map.of("type", "text-start", "id", textId));
            AssistantChatLlmClient.StreamResult llmResult = llmClient.stream(
                    userId,
                    messages,
                    new AssistantChatLlmClient.ToolEventListener() {
                        @Override
                        public void onToolInputAvailable(String toolCallId, String toolName, Object input) {
                            sendChunk(emitter, Map.of(
                                    "type", "tool-input-available",
                                    "toolCallId", toolCallId,
                                    "toolName", toolName,
                                    "input", input,
                                    "providerExecuted", true
                            ));
                        }

                        @Override
                        public void onToolOutputAvailable(String toolCallId, Object output) {
                            sendChunk(emitter, Map.of(
                                    "type", "tool-output-available",
                                    "toolCallId", toolCallId,
                                    "output", output,
                                    "providerExecuted", true
                            ));
                        }

                        @Override
                        public void onToolOutputError(String toolCallId, String errorText) {
                            sendChunk(emitter, Map.of(
                                    "type", "tool-output-error",
                                    "toolCallId", toolCallId,
                                    "errorText", errorText,
                                    "providerExecuted", true
                            ));
                        }
                    },
                    delta -> sendChunk(emitter, Map.of("type", "text-delta", "id", textId, "delta", delta))
            );
            sendChunk(emitter, Map.of("type", "text-end", "id", textId));

            for (var source : rag.sources()) {
                Map<String, Object> sourceChunk = new LinkedHashMap<>();
                sourceChunk.put("type", "source-url");
                sourceChunk.put("sourceId", source.sourceId());
                sourceChunk.put("url", source.url());
                sourceChunk.put("title", source.title());
                sendChunk(emitter, sourceChunk);
            }

            String finishReason = llmResult != null ? llmResult.finishReason() : null;
            sendChunk(emitter, Map.of("type", "finish", "finishReason", normalizeFinishReason(finishReason)));
            sendDone(emitter);
        } catch (Exception e) {
            log.warn("assistant chat failed", e);
            sendErrorAndDone(emitter, "Assistant error: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(AssistantRagContextService.RagContext rag, Object toolResult) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(systemPromptTemplate.base()).append("\n");
        if (toolResult != null) {
            sb.append("\n# Tool result (server-side)\n");
            sb.append(toolResult);
            sb.append("\n");
        }
        if (!rag.contextText().isBlank()) {
            sb.append("\n# Retrieved context (Help Center)\n");
            sb.append(rag.contextText());
        }
        return sb.toString();
    }

    private void sendChunk(SseEmitter emitter, Object chunk) {
        try {
            String json = objectMapper.writeValueAsString(chunk);
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (Exception ignored) {
        } finally {
            emitter.complete();
        }
    }

    private void sendErrorAndDone(SseEmitter emitter, String errorText) {
        try {
            sendChunk(emitter, Map.of("type", "error", "errorText", errorText));
            sendChunk(emitter, Map.of("type", "finish", "finishReason", "error"));
        } catch (Exception ignored) {
        } finally {
            sendDone(emitter);
        }
    }

    private static String normalizeFinishReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return "stop";
        }
        String value = raw.trim();
        return switch (value) {
            case "stop", "length", "content-filter", "tool-calls", "error", "other" -> value;
            case "tool_calls" -> "tool-calls";
            default -> "other";
        };
    }
}
