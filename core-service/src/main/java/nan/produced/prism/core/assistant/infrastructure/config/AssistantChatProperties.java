package nan.produced.prism.core.assistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.chat")
public record AssistantChatProperties(
        Prompt prompt,
        Rag rag,
        Llm llm,
        Providers providers
) {

    public record Prompt(
            String systemTemplate
    ) {
    }

    public record Rag(
            boolean enabled,
            int topK,
            int maxContextChars,
            String preferLang
    ) {
    }

    public record Llm(
            String baseUrl,
            String model,
            String apiKey,
            double temperature
    ) {
    }

    public record Providers(
            Provider openai,
            Provider gemini
    ) {
    }

    public record Provider(
            String baseUrl,
            String defaultModel
    ) {
    }
}
