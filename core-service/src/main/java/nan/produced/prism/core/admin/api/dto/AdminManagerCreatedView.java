package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端-创建 Manager 响应（包含一次性初始密码）")
public record AdminManagerCreatedView(
    @Schema(description = "publicId")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID")
    String userUuid,
    @Schema(description = "username")
    String username,
    @Schema(description = "一次性初始密码（仅本次返回）")
    String initialPassword
) {
}

