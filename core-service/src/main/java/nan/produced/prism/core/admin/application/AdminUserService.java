package nan.produced.prism.core.admin.application;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.admin.api.dto.AdminUserSearchItemView;
import nan.produced.prism.core.admin.api.dto.AdminUserSearchPageView;
import nan.produced.prism.core.admin.api.dto.AdminUserDetailView;
import nan.produced.prism.core.admin.api.dto.AdminUserSessionsRevokeResult;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthAccountSecurityInternalClient;
import nan.produced.prism.core.integration.auth.client.AuthApiKeyInternalClient;
import nan.produced.prism.core.integration.auth.client.AuthInternalClient;
import nan.produced.prism.core.integration.auth.client.AuthSubscriptionInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyView;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserResponse;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserSearchItem;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserSearchPageView;
import nan.produced.prism.core.integration.auth.dto.AuthRememberedDeviceView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionRedeemRequest;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserActiveSessionView;
import nan.produced.prism.core.user.dto.UserApiKeyView;
import nan.produced.prism.core.user.dto.UserSubscriptionView;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final AuthInternalClient authInternalClient;
    private final AuthSubscriptionInternalClient authSubscriptionInternalClient;
    private final AuthApiKeyInternalClient authApiKeyInternalClient;
    private final AuthAccountSecurityInternalClient authAccountSecurityInternalClient;
    private final GatewaySessionRevoker gatewaySessionRevoker;
    private final UserProfileRepository userProfileRepository;

    public AdminUserDetailView getUserDetail(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);

        UserProfileEntity profile = userProfileRepository.findByPublicId(remote.publicId()).orElse(null);
        String displayName = profile != null ? profile.getDisplayName() : null;

        UserSubscriptionView subscription = getSubscription(remote.userId());
        List<UserApiKeyView> apiKeys = listApiKeys(remote.userId());
        List<UserActiveSessionView> sessions = listRememberMeDevices(remote.userId());

        return new AdminUserDetailView(
            remote.publicId(),
            remote.userId().toString(),
            remote.email(),
            remote.phone(),
            displayName,
            profile != null,
            subscription,
            apiKeys,
            sessions
        );
    }

    public AdminUserSearchPageView searchUsers(String q, int page, int size) {
        if (!StringUtils.hasText(q)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "q is required");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        ApiResponse<AuthInternalUserSearchPageView> response = authInternalClient.search(q.trim(), safePage, safeSize);
        AuthInternalUserSearchPageView remote = requireAuthSuccess(response, "搜索用户失败");
        if (remote == null || remote.items() == null) {
            return new AdminUserSearchPageView(List.of(), safePage, safeSize, 0);
        }
        List<AdminUserSearchItemView> items = remote.items().stream()
            .map(this::toSearchItem)
            .toList();
        return new AdminUserSearchPageView(items, remote.page(), remote.size(), remote.total());
    }

    public void lockUser(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);
        ApiResponse<Object> resp = authInternalClient.lock(remote.userId().toString());
        requireAuthSuccess(resp, "锁定用户失败");
    }

    public void unlockUser(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);
        ApiResponse<Object> resp = authInternalClient.unlock(remote.userId().toString());
        requireAuthSuccess(resp, "解锁用户失败");
    }

    public List<UserApiKeyView> listUserApiKeys(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);
        return listApiKeys(remote.userId());
    }

    public void revokeUserApiKey(String publicId, String apiKeyId) {
        if (!StringUtils.hasText(apiKeyId)) {
            return;
        }
        RemoteUser remote = requireRemoteUser(publicId);
        ApiResponse<Object> response = authApiKeyInternalClient.revokeApiKey(remote.userId(), apiKeyId);
        requireAuthSuccess(response, "撤销 API Key 失败");
    }

    public UserSubscriptionView getUserSubscription(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);
        return getSubscription(remote.userId());
    }

    public UserSubscriptionView redeemUserSubscription(String publicId, String code) {
        RemoteUser remote = requireRemoteUser(publicId);
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "code is required");
        }
        ApiResponse<AuthSubscriptionView> response = authSubscriptionInternalClient.redeem(remote.userId(), new AuthSubscriptionRedeemRequest(code.trim()));
        AuthSubscriptionView updated = requireAuthSuccessWithRedeemMapping(response, "兑换订阅失败");
        if (updated == null) {
            return new UserSubscriptionView("FREE", null, null, false);
        }
        String tier = StringUtils.hasText(updated.tier()) ? updated.tier() : "FREE";
        return new UserSubscriptionView(tier, updated.startAt(), updated.endAt(), updated.proActive());
    }

    public AdminUserSessionsRevokeResult revokeAllUserSessions(String publicId) {
        RemoteUser remote = requireRemoteUser(publicId);

        ApiResponse<Object> revokeRememberMe = authAccountSecurityInternalClient.revokeAllRememberMeTokens(remote.userId());
        requireAuthSuccess(revokeRememberMe, "撤销 remember-me tokens 失败");

        int gatewaySessions = gatewaySessionRevoker.revokeAllByPrincipalName(remote.publicId());

        return new AdminUserSessionsRevokeResult(remote.publicId(), remote.userId().toString(), gatewaySessions);
    }

    private UserSubscriptionView getSubscription(UUID userId) {
        ApiResponse<AuthSubscriptionView> response = authSubscriptionInternalClient.getCurrent(userId);
        AuthSubscriptionView remote = requireAuthSuccess(response, "查询订阅失败");
        if (remote == null) {
            return new UserSubscriptionView("FREE", null, null, false);
        }
        String tier = StringUtils.hasText(remote.tier()) ? remote.tier() : "FREE";
        return new UserSubscriptionView(tier, remote.startAt(), remote.endAt(), remote.proActive());
    }

    private List<UserApiKeyView> listApiKeys(UUID userId) {
        ApiResponse<List<AuthApiKeyView>> response = authApiKeyInternalClient.listApiKeys(userId);
        List<AuthApiKeyView> keys = requireAuthSuccess(response, "查询 API Keys 失败");
        if (keys == null) {
            return List.of();
        }
        return keys.stream()
            .map(it -> new UserApiKeyView(it.id(), it.name(), it.clientId(), it.clientSecret(), it.createdAt(), it.lastUsedAt()))
            .toList();
    }

    private List<UserActiveSessionView> listRememberMeDevices(UUID userId) {
        ApiResponse<List<AuthRememberedDeviceView>> response = authAccountSecurityInternalClient.listRememberMeTokens(userId, null);
        List<AuthRememberedDeviceView> devices = requireAuthSuccess(response, "查询活跃会话失败");
        if (devices == null) {
            return List.of();
        }
        return devices.stream()
            .map(it -> new UserActiveSessionView(
                it.series(),
                it.deviceName(),
                it.ipAddress(),
                it.userAgent(),
                it.createdAt(),
                it.lastUsedAt(),
                it.expiresAt(),
                it.current()
            ))
            .toList();
    }

    private AdminUserSearchItemView toSearchItem(AuthInternalUserSearchItem item) {
        if (item == null) {
            return null;
        }
        return new AdminUserSearchItemView(
            item.publicId(),
            item.email(),
            item.phone(),
            item.status(),
            item.createdAt()
        );
    }

    private RemoteUser requireRemoteUser(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "publicId is required");
        }

        ApiResponse<AuthInternalUserResponse> response = authInternalClient.getUserByPublicId(publicId);
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "从认证中心获取用户资料失败: null 响应");
        }
        if (AUTH_SUCCESS_CODE.equals(response.getCode()) && response.getData() != null) {
            AuthInternalUserResponse data = response.getData();
            return new RemoteUser(
                data.getPublicId(),
                parseUuid(data.getUserId(), "userId"),
                data.getEmail(),
                data.getPhone()
            );
        }
        if ("AUTH-1007".equals(response.getCode())) {
            throw new BizException(ErrorCode.USER_NOT_FOUND_IN_AUTH, "User not found: " + publicId);
        }
        throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "从认证中心获取用户资料失败: " + response.getMessage());
    }

    private UUID parseUuid(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, fieldName + " is missing in auth response");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Invalid " + fieldName + " in auth response: " + raw, ex);
        }
    }

    private <T> T requireAuthSuccess(ApiResponse<T> response, String message) {
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": null response");
        }
        if (!AUTH_SUCCESS_CODE.equals(response.getCode())) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
        }
        return response.getData();
    }

    private <T> T requireAuthSuccessWithRedeemMapping(ApiResponse<T> response, String message) {
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": null response");
        }
        if (AUTH_SUCCESS_CODE.equals(response.getCode())) {
            return response.getData();
        }
        String remoteCode = response.getCode();
        if ("AUTH-1030".equals(remoteCode)) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
    }

    private record RemoteUser(String publicId, UUID userId, String email, String phone) {
    }
}
