package nan.produced.prism.core.assistant.infrastructure.springai;

import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AssistantChatModelFactory {

    private final ToolCallingManager toolCallingManager;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;

    public String resolveApiKey(AssistantChatModelRouter.LlmTarget target) {
        String apiKey = target != null ? target.apiKey() : null;
        return StringUtils.hasText(apiKey) ? apiKey : "local";
    }

    public OpenAiApi buildApi(AssistantChatModelRouter.LlmTarget target, String apiKey) {
        return OpenAiApi.builder()
                .baseUrl(target.baseUrl())
                .apiKey(apiKey)
                .build();
    }

    public String resolveModel(AssistantChatModelRouter.LlmTarget target, String localVllmModelOverride) {
        String model = target.model();
        if ("local-vllm".equalsIgnoreCase(target.provider()) && StringUtils.hasText(localVllmModelOverride)) {
            model = localVllmModelOverride;
        }
        return model;
    }

    public OpenAiChatOptions buildChatOptions(AssistantChatModelRouter.LlmTarget target,
                                              String model,
                                              AssistantChatLlmClient.StreamOptions streamOptions) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(target.temperature())
                .internalToolExecutionEnabled(false)
                .parallelToolCalls(false)
                .toolChoice("none");

        Integer maxCompletionTokens = streamOptions != null && streamOptions.maxCompletionTokens() > 0
                ? streamOptions.maxCompletionTokens()
                : null;
        if (maxCompletionTokens != null) {
            // vLLM is OpenAI-compatible and typically supports max_tokens; OpenAI may prefer max_completion_tokens.
            builder.maxTokens(maxCompletionTokens);
            builder.maxCompletionTokens(maxCompletionTokens);
        }

        return builder.build();
    }

    public OpenAiChatModel buildChatModel(OpenAiApi api, OpenAiChatOptions options) {
        return new OpenAiChatModel(api, options, toolCallingManager, retryTemplate, observationRegistry);
    }
}
