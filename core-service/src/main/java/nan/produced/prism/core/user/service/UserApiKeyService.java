package nan.produced.prism.core.user.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthApiKeyInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyView;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.user.dto.UserApiKeyCreateRequest;
import nan.produced.prism.core.user.dto.UserApiKeyView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserApiKeyService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final AuthApiKeyInternalClient authApiKeyInternalClient;

    public List<UserApiKeyView> listCurrentUserApiKeys() {
        UUID userId = requireCurrentUserUuid();
        ApiResponse<List<AuthApiKeyView>> response = authApiKeyInternalClient.listApiKeys(userId);
        List<AuthApiKeyView> keys = requireAuthSuccess(response, "查询 API Keys 失败");
        if (keys == null) {
            return List.of();
        }
        return keys.stream().map(this::toUserView).toList();
    }

    public UserApiKeyView createCurrentUserApiKey(UserApiKeyCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "name is required");
        }
        UUID userId = requireCurrentUserUuid();
        ApiResponse<AuthApiKeyView> response = authApiKeyInternalClient.createApiKey(userId, new AuthApiKeyCreateRequest(request.name()));
        AuthApiKeyView created = requireAuthSuccess(response, "创建 API Key 失败");
        return toUserView(created);
    }

    public UserApiKeyView regenerateCurrentUserApiKeySecret(String apiKeyId) {
        if (!StringUtils.hasText(apiKeyId)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "apiKeyId is required");
        }
        UUID userId = requireCurrentUserUuid();
        ApiResponse<AuthApiKeyView> response = authApiKeyInternalClient.regenerateSecret(userId, apiKeyId);
        AuthApiKeyView updated = requireAuthSuccess(response, "重置 API Key 失败");
        return toUserView(updated);
    }

    public void revokeCurrentUserApiKey(String apiKeyId) {
        if (!StringUtils.hasText(apiKeyId)) {
            return;
        }
        UUID userId = requireCurrentUserUuid();
        ApiResponse<Object> response = authApiKeyInternalClient.revokeApiKey(userId, apiKeyId);
        requireAuthSuccess(response, "撤销 API Key 失败");
    }

    private UserApiKeyView toUserView(AuthApiKeyView view) {
        if (view == null) {
            return null;
        }
        return new UserApiKeyView(
            view.id(),
            view.name(),
            view.clientId(),
            view.clientSecret(),
            view.createdAt(),
            view.lastUsedAt()
        );
    }

    private UUID requireCurrentUserUuid() {
        String userUuid = CloudAuthContext.getCurrentUser().userUuid();
        if (!StringUtils.hasText(userUuid)) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER, "userUuid is missing in CLOUD_AUTH");
        }
        try {
            return UUID.fromString(userUuid);
        } catch (IllegalArgumentException ex) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER, "Invalid userUuid in CLOUD_AUTH: " + userUuid, ex);
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
}
