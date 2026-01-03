package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "管理端-Manager 账号项")
public record AdminManagerItemView(
    @Schema(description = "publicId（v1 与 username 相同）")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID")
    String userUuid,
    @Schema(description = "username")
    String username,
    @Schema(description = "状态（ACTIVE/LOCKED/DELETED）")
    String status,
    @Schema(description = "创建时间")
    Instant createdAt
) {
}

