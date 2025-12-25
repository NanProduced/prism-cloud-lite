package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "确认绑定手机号")
public record UserConfirmPhoneBindRequest(
    @Schema(description = "手机号", example = "13800138000")
    String phone,
    @Schema(description = "短信验证码", example = "123456")
    String code
) {}

