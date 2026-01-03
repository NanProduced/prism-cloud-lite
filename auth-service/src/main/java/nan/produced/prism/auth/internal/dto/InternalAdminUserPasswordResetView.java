package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "内部接口-重置密码响应（包含一次性新密码）")
public record InternalAdminUserPasswordResetView(
    @Schema(description = "publicId")
    String publicId,
    @Schema(description = "Auth-Service 内部 UUID")
    String userId,
    @Schema(description = "一次性新密码（仅本次返回）")
    String newPassword
) {
}

