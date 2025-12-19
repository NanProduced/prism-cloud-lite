package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "账号安全 - 修改密码请求")
public record UserChangePasswordRequest(
    @Schema(description = "当前密码", requiredMode = Schema.RequiredMode.REQUIRED)
    String currentPassword,
    @Schema(description = "新密码（至少8位，需包含大小写字母和数字）", requiredMode = Schema.RequiredMode.REQUIRED)
    String newPassword
) {}

