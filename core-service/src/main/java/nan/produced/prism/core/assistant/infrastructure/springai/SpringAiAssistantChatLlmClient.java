package nan.produced.prism.core.assistant.infrastructure.springai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SpringAiAssistantChatLlmClient implements AssistantChatLlmClient {

    private final AssistantChatModelRouter router;
    private final AssistantChatModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private volatile String localVllmModelOverride;

    public SpringAiAssistantChatLlmClient(AssistantChatModelRouter router,
                                         AssistantChatModelFactory modelFactory,
                                         ObjectMapper objectMapper) {
        this.router = router;
        this.modelFactory = modelFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public StreamResult stream(UUID userId,
                               List<AssistantChatMessage> messages,
                               StreamOptions streamOptions,
                               Consumer<String> onDelta) {
        AssistantChatModelRouter.LlmTarget target = router.resolveForUser(userId);

        String apiKey = modelFactory.resolveApiKey(target);
        OpenAiApi api = modelFactory.buildApi(target, apiKey);

        String model = modelFactory.resolveModel(target, localVllmModelOverride);
        ChatModelState chatState = buildChatModelState(userId, target, api, model, streamOptions);

        List<Message> history = toSpringAiMessages(messages);

        boolean modelFallbackAttempted = false;

        while (true) {
            long promptTokens = -1;
            long completionTokens = -1;
            long totalTokens = -1;
            String finishReason = null;
            boolean receivedAny = false;

            try {
                var flux = chatState.model().stream(new Prompt(history, chatState.options()));
                for (ChatResponse response : flux.toIterable()) {
                    if (response == null) {
                        continue;
                    }
                    receivedAny = true;

                    var usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) {
                            promptTokens = usage.getPromptTokens();
                        }
                        if (usage.getCompletionTokens() != null) {
                            completionTokens = usage.getCompletionTokens();
                        }
                        if (usage.getTotalTokens() != null) {
                            totalTokens = usage.getTotalTokens();
                        }
                    }

                    var result = response.getResult();
                    if (result != null && result.getMetadata() != null && StringUtils.hasText(result.getMetadata().getFinishReason())) {
                        finishReason = result.getMetadata().getFinishReason();
                    }

                    var assistant = result != null ? result.getOutput() : null;
                    String content = assistant != null ? assistant.getText() : null;
                    if (StringUtils.hasText(content) && onDelta != null) {
                        onDelta.accept(content);
                    }
                }
            } catch (Exception ex) {
                if (!modelFallbackAttempted
                        && "local-vllm".equalsIgnoreCase(target.provider())
                        && isModelNotFound(ex)) {
                    String fallbackModel = resolveFirstModelId(target.baseUrl(), apiKey);
                    String configuredModel = target.model();
                    if (StringUtils.hasText(fallbackModel)
                            && (!StringUtils.hasText(configuredModel) || !fallbackModel.equalsIgnoreCase(configuredModel))) {
                        modelFallbackAttempted = true;
                        localVllmModelOverride = fallbackModel;
                        model = fallbackModel;
                        chatState = buildChatModelState(userId, target, api, model, streamOptions);
                        continue;
                    }
                }
                throw ex;
            }

            if (!receivedAny) {
                return new StreamResult("error", null, null, null);
            }

            Integer pt = safeIntOrNull(promptTokens);
            Integer ct = safeIntOrNull(completionTokens);
            Integer tt = safeIntOrNull(totalTokens);
            if (tt == null && promptTokens > 0 && completionTokens > 0) {
                tt = safeIntOrNull(promptTokens + completionTokens);
            }
            return new StreamResult(finishReason, pt, ct, tt);
        }
    }

    private record ChatModelState(OpenAiChatModel model, OpenAiChatOptions options) {
    }

    private ChatModelState buildChatModelState(UUID userId,
                                               AssistantChatModelRouter.LlmTarget target,
                                               OpenAiApi api,
                                               String model,
                                               StreamOptions streamOptions) {
        OpenAiChatOptions options = modelFactory.buildChatOptions(target, model, streamOptions);
        OpenAiChatModel chatModel = modelFactory.buildChatModel(api, options);
        return new ChatModelState(chatModel, options);
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

    private static List<Message> toSpringAiMessages(List<AssistantChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (AssistantChatMessage m : messages) {
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
}
