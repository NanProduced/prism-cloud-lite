package nan.produced.prism.core.assistant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "UpsertUserAiModelConfigReq", description = "新增/更新用户 AI 模型配置（BYOK）")
public record UpsertUserAiModelConfigReq(
        @Schema(description = "模型提供方", example = "openai", allowableValues = {"local-vllm", "openai", "gemini"})
        @NotBlank
        String provider,
        @Schema(description = "模型名称（provider 侧的 model id）", example = "gpt-4o-mini")
        String model,
        @Schema(description = "是否启用（默认 true）", example = "true")
        Boolean enabled,
        @Schema(description = "是否设置为默认 provider", example = "false")
        Boolean makeDefault,
        @Schema(description = "API Key（仅写入，不回显）", example = "sk-***", accessMode = Schema.AccessMode.WRITE_ONLY)
        String apiKey
) {
}
