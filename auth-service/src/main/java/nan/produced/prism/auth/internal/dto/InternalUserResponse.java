package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "用于内部服务的用户资料响应")
public class InternalUserResponse {
    @Schema(description = "用户 publicId")
    String publicId;

    @Schema(description = "Auth-Service 内部 UUID")
    String userId;

    @Schema(description = "邮箱")
    String email;

    @Schema(description = "手机号")
    String phone;
}
