package nan.produced.prism.core.user.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthSubscriptionInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionHistoryPageView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionRedeemRequest;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionView;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.user.dto.UserSubscriptionEventView;
import nan.produced.prism.core.user.dto.UserSubscriptionHistoryView;
import nan.produced.prism.core.user.dto.UserSubscriptionRedeemRequest;
import nan.produced.prism.core.user.dto.UserSubscriptionView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserSubscriptionService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final AuthSubscriptionInternalClient authSubscriptionInternalClient;

    public UserSubscriptionView getCurrentUserSubscription() {
        UUID userId = requireCurrentUserUuid();
        ApiResponse<AuthSubscriptionView> response = authSubscriptionInternalClient.getCurrent(userId);
        AuthSubscriptionView remote = requireAuthSuccess(response, "查询订阅失败");
        return toView(remote);
    }

    public UserSubscriptionView redeemCurrentUserSubscription(UserSubscriptionRedeemRequest request) {
        UUID userId = requireCurrentUserUuid();
        if (request == null || !StringUtils.hasText(request.code())) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "code is required");
        }

        ApiResponse<AuthSubscriptionView> response = authSubscriptionInternalClient.redeem(
            userId,
            new AuthSubscriptionRedeemRequest(request.code())
        );
        AuthSubscriptionView remote = requireAuthSuccessWithMapping(response, "兑换订阅失败");
        return toView(remote);
    }

    public UserSubscriptionHistoryView listCurrentUserSubscriptionHistory(int page, int size) {
        UUID userId = requireCurrentUserUuid();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        ApiResponse<AuthSubscriptionHistoryPageView> response = authSubscriptionInternalClient.listEvents(userId, safePage, safeSize);
        AuthSubscriptionHistoryPageView history = requireAuthSuccess(response, "查询订阅审计失败");
        if (history == null || history.items() == null) {
            return new UserSubscriptionHistoryView(List.of(), safePage, safeSize, 0);
        }
        return new UserSubscriptionHistoryView(
            history.items().stream()
                .map(it -> new UserSubscriptionEventView(
                    it.id(),
                    it.type(),
                    it.success(),
                    it.code(),
                    it.metadata(),
                    it.createdAt()
                ))
                .toList(),
            history.page(),
            history.size(),
            history.total()
        );
    }

    private UserSubscriptionView toView(AuthSubscriptionView remote) {
        if (remote == null) {
            return new UserSubscriptionView("FREE", null, null, false);
        }
        String tier = StringUtils.hasText(remote.tier()) ? remote.tier() : "FREE";
        return new UserSubscriptionView(tier, remote.startAt(), remote.endAt(), remote.proActive());
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

    private <T> T requireAuthSuccessWithMapping(ApiResponse<T> response, String message) {
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": null response");
        }
        if (AUTH_SUCCESS_CODE.equals(response.getCode())) {
            return response.getData();
        }

        String remoteCode = response.getCode();
        if (!StringUtils.hasText(remoteCode)) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
        }
        if ("AUTH-1030".equals(remoteCode)) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
    }
}

