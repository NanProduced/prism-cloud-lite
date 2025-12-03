package nan.produced.prism.core.security;

import java.util.List;

/**
 * 从 CLOUD_AUTH 头解析出的用户信息
 * Gateway 在认证后将用户信息通过此格式传递给下游服务
 *
 * @param publicId 认证用户的 publicId（来自 JWT sub claim）
 *                 这是用户在整个系统中的唯一标识符
 * @param userUuid 认证用户的 UUID（来自 JWT user_uuid claim）
 *                 Auth-Service 的内部 ID
 * @param roles    用户角色列表（来自 JWT roles claim）
 *                 例如: ["ROLE_USER", "ROLE_ADMIN"]
 * @param tier     订阅层级（来自 JWT tier claim）
 *                 例如: "FREE", "PRO"
 */
public record CloudAuthUser(String publicId, String userUuid, List<String> roles, String tier) {
}
