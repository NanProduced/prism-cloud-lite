package nan.produced.prism.core.assistant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserAiModelConfigResp", description = "用户 AI 模型配置（不包含明文 apiKey）")
public record UserAiModelConfigResp(
        @Schema(description = "模型提供方", example = "openai", allowableValues = {"local-vllm", "openai", "gemini"})
        String provider,
        @Schema(description = "模型名称（provider 侧的 model id）", example = "gpt-4o-mini")
        String model,
        @Schema(description = "是否启用", example = "true")
        boolean enabled,
        @Schema(description = "是否默认 provider", example = "true")
        boolean isDefault,
        @Schema(description = "是否已配置 apiKey（不会回显）", example = "true")
        boolean hasApiKey,
        @Schema(description = "apiKey 最后 4 位（用于 UI 展示）", example = "a1b2")
        String apiKeyLast4
) {
}
