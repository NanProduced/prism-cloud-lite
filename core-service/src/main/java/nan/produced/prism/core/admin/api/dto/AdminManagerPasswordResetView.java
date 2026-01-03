package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端-重置 Manager 密码结果")
public record AdminManagerPasswordResetView(
    @Schema(description = "publicId")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID")
    String userUuid,
    @Schema(description = "一次性新密码（仅本次返回）")
    String newPassword
) {
}

