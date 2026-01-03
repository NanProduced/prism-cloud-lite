package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "内部接口-用户搜索项")
public class InternalUserSearchItem {
    @Schema(description = "用户 publicId")
    String publicId;

    @Schema(description = "Auth-Service 内部 UUID")
    String userId;

    @Schema(description = "邮箱")
    String email;

    @Schema(description = "手机号")
    String phone;

    @Schema(description = "状态（ACTIVE/LOCKED/DELETED）")
    String status;

    @Schema(description = "创建时间")
    Instant createdAt;
}

