package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 API Key（internal）")
public record InternalApiKeyCreateRequest(
    @Schema(description = "API Key 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    String name
) {
}

