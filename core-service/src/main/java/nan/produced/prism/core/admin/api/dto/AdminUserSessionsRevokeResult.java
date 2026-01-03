package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "强制下线结果")
public record AdminUserSessionsRevokeResult(
    @Schema(description = "用户 publicId")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID")
    String userUuid,
    @Schema(description = "删除的 gateway session 数量（按 principalName 索引）")
    int gatewaySessionsDeleted
) {
}

