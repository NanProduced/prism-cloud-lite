package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import nan.produced.prism.core.user.dto.UserActiveSessionView;
import nan.produced.prism.core.user.dto.UserApiKeyView;
import nan.produced.prism.core.user.dto.UserSubscriptionView;

@Schema(description = "管理端用户详情（聚合）")
public record AdminUserDetailView(
    @Schema(description = "用户 publicId")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID")
    String userUuid,
    @Schema(description = "邮箱（来自认证中心）")
    String email,
    @Schema(description = "手机号（来自认证中心）")
    String phone,
    @Schema(description = "displayName（来自 core profile，可能为 null）")
    String displayName,
    @Schema(description = "core profile 是否存在（便于排查 JIT provisioning）")
    boolean coreProfileExists,
    @Schema(description = "订阅信息（来自认证中心）")
    UserSubscriptionView subscription,
    @Schema(description = "API Keys（来自认证中心）")
    List<UserApiKeyView> apiKeys,
    @Schema(description = "remember-me 设备（来自认证中心）")
    List<UserActiveSessionView> activeSessions
) {
}

