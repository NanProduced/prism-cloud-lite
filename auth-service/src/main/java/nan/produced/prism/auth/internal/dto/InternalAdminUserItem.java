package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "内部接口-管理账号项（Admin/Manager）")
public class InternalAdminUserItem {
    @Schema(description = "publicId（v1 与 username 相同）")
    String publicId;

    @Schema(description = "Auth-Service 内部 UUID")
    String userId;

    @Schema(description = "username（v1 存储在 email 字段中）")
    String username;

    @Schema(description = "用户类型（ADMIN/MANAGER）")
    String userType;

    @Schema(description = "状态（ACTIVE/LOCKED/DELETED）")
    String status;

    @Schema(description = "创建时间")
    Instant createdAt;
}

