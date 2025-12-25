package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "请求绑定手机号验证码")
public record UserRequestPhoneBindOtpRequest(
    @Schema(description = "手机号", example = "13800138000")
    String phone
) {}

