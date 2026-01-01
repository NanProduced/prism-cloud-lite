package nan.produced.prism.core.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertUserAiModelConfigReq(
        @NotBlank String provider,
        String model,
        Boolean enabled,
        Boolean makeDefault,
        String apiKey
) {
}

