package nan.produced.prism.core.assistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.credentials")
public record AssistantCredentialsProperties(
        String masterKey
) {
}

