package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建 API Key 请求体")
public record UserApiKeyCreateRequest(
    @Schema(description = "API Key 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    String name
) {
}

