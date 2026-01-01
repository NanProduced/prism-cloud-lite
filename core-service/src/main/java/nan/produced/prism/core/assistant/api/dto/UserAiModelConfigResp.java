package nan.produced.prism.core.assistant.api.dto;

public record UserAiModelConfigResp(
        String provider,
        String model,
        boolean enabled,
        boolean isDefault,
        boolean hasApiKey,
        String apiKeyLast4
) {
}

