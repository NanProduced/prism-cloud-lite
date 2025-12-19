package nan.produced.prism.core.user.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthAccountSecurityInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthChangePasswordRequest;
import nan.produced.prism.core.integration.auth.dto.AuthRememberedDeviceView;
import nan.produced.prism.core.security.CloudAuthContext;
import nan.produced.prism.core.user.dto.UserActiveSessionView;
import nan.produced.prism.core.user.dto.UserChangePasswordRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserSecurityService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";
    private static final String REMEMBER_ME_COOKIE_NAME = "prism-remember-me";

    private final AuthAccountSecurityInternalClient authAccountSecurityInternalClient;

    public List<UserActiveSessionView> listCurrentUserActiveSessions(HttpServletRequest request) {
        UUID userId = requireCurrentUserUuid();
        String activeSeries = extractRememberMeSeries(request);

        ApiResponse<List<AuthRememberedDeviceView>> response =
            authAccountSecurityInternalClient.listRememberMeTokens(userId, activeSeries);

        List<AuthRememberedDeviceView> devices = requireAuthSuccess(response, "查询活跃会话失败");
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

    public void revokeCurrentUserSession(String series, HttpServletRequest request, HttpServletResponse response) {
        UUID userId = requireCurrentUserUuid();
        if (!StringUtils.hasText(series)) {
            return;
        }

        ApiResponse<Object> resp = authAccountSecurityInternalClient.revokeRememberMeToken(userId, series);
        requireAuthSuccess(resp, "注销会话失败");

        String activeSeries = extractRememberMeSeries(request);
        if (series.equals(activeSeries)) {
            clearRememberMeCookie(request, response);
        }
    }

    public void revokeAllCurrentUserSessions(HttpServletRequest request, HttpServletResponse response) {
        UUID userId = requireCurrentUserUuid();
        ApiResponse<Object> resp = authAccountSecurityInternalClient.revokeAllRememberMeTokens(userId);
        requireAuthSuccess(resp, "注销全部会话失败");
        clearRememberMeCookie(request, response);
    }

    public void changeCurrentUserPassword(UserChangePasswordRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "request body is required");
        }
        if (!StringUtils.hasText(request.currentPassword())) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "currentPassword is required");
        }
        if (!StringUtils.hasText(request.newPassword())) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "newPassword is required");
        }

        UUID userId = requireCurrentUserUuid();
        ApiResponse<Object> resp = authAccountSecurityInternalClient.changePassword(
            userId,
            new AuthChangePasswordRequest(request.currentPassword(), request.newPassword())
        );
        requireAuthSuccess(resp, "修改密码失败");

        // 密码变更后，注销所有 remember-me 设备（auth-service 已处理），同时清理本地 cookie。
        clearRememberMeCookie(httpRequest, httpResponse);
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

    private String extractRememberMeSeries(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie != null && REMEMBER_ME_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                String value = cookie.getValue();
                int idx = value.indexOf(':');
                if (idx > 0) {
                    return value.substring(0, idx);
                }
            }
        }
        return null;
    }

    private void clearRememberMeCookie(HttpServletRequest request, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(REMEMBER_ME_COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request != null && request.isSecure());
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
