package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "内部接口-创建管理账号响应（包含一次性初始密码）")
public record InternalAdminUserCreatedView(
    @Schema(description = "publicId（v1 与 username 相同）")
    String publicId,
    @Schema(description = "Auth-Service 内部 UUID")
    String userId,
    @Schema(description = "username")
    String username,
    @Schema(description = "一次性初始密码（仅创建/重置时返回）")
    String initialPassword
) {
}

