package nan.produced.prism.core.user.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthAccountSecurityInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthChangePasswordRequest;
import nan.produced.prism.core.integration.auth.dto.AuthConfirmPhoneBindRequest;
import nan.produced.prism.core.integration.auth.dto.AuthRememberedDeviceView;
import nan.produced.prism.core.integration.auth.dto.AuthRequestPhoneBindOtpRequest;
import nan.produced.prism.core.integration.auth.dto.AuthSecurityHistoryPageView;
import nan.produced.prism.core.integration.auth.dto.AuthSecurityEventView;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.user.dto.UserChangePasswordRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSecurityService 单元测试")
class UserSecurityServiceTests {

    @Mock
    private AuthAccountSecurityInternalClient authAccountSecurityInternalClient;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private UserSecurityService userSecurityService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_PUBLIC_ID = "test_public_id_1234";
    private static final String TEST_SERIES = "test_series_123";
    private static final String TEST_PHONE = "13800138000";
    private static final String TEST_OTP_CODE = "123456";
    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";
    private static final String REMEMBER_ME_COOKIE_NAME = "prism-remember-me";

    @BeforeEach
    void setUp() {
        cloudAuthContextMock = mockStatic(CloudAuthContext.class);
    }

    @AfterEach
    void tearDown() {
        cloudAuthContextMock.close();
    }

    private void mockCurrentUser() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, TEST_USER_ID.toString(), null, "FREE");
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private void mockCurrentUserWithNullUuid() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, null, null, "FREE");
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private void mockCurrentUserWithInvalidUuid() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, "invalid-uuid", null, "FREE");
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private AuthRememberedDeviceView createTestAuthDeviceView(String series, boolean current) {
        return new AuthRememberedDeviceView(
                series,
                "Test Device",
                "192.168.1.1",
                "Mozilla/5.0",
                Instant.now().minusSeconds(86400),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                current
        );
    }

    private AuthSecurityEventView createTestAuthEventView(Long id, String type, boolean success) {
        return new AuthSecurityEventView(
                id,
                type,
                success,
                "192.168.1.1",
                "Test Device",
                "Mozilla/5.0",
                "{\"key\":\"value\"}",
                Instant.now()
        );
    }

    private <T> ApiResponse<T> createAuthSuccessResponse(T data) {
        return ApiResponse.<T>builder()
                .code(AUTH_SUCCESS_CODE)
                .message("OK")
                .data(data)
                .build();
    }

    private ApiResponse<Object> createAuthErrorResponse(String code, String message) {
        return ApiResponse.<Object>builder()
                .code(code)
                .message(message)
                .data(null)
                .build();
    }

    private void mockRememberMeCookie(String series) {
        String cookieValue = series + ":token_value";
        Cookie cookie = new Cookie(REMEMBER_ME_COOKIE_NAME, cookieValue);
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
    }

    @Nested
    @DisplayName("listCurrentUserActiveSessions 方法测试")
    class ListCurrentUserActiveSessionsTests {

        @Test
        @DisplayName("当认证中心返回设备列表时，正确转换并返回")
        void whenAuthServiceReturnsDevices_shouldConvertAndReturn() {
            mockCurrentUser();
            List<AuthRememberedDeviceView> authDevices = List.of(
                    createTestAuthDeviceView("series_1", true),
                    createTestAuthDeviceView("series_2", false)
            );
            ApiResponse<List<AuthRememberedDeviceView>> apiResponse = createAuthSuccessResponse(authDevices);

            when(authAccountSecurityInternalClient.listRememberMeTokens(eq(TEST_USER_ID), eq(null))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserActiveSessions(request);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).series()).isEqualTo("series_1");
            assertThat(result.get(0).current()).isTrue();
            assertThat(result.get(1).series()).isEqualTo("series_2");
            assertThat(result.get(1).current()).isFalse();
        }

        @Test
        @DisplayName("当存在remember-me cookie时，提取series并传递给认证中心")
        void whenRememberMeCookieExists_shouldExtractSeries() {
            mockCurrentUser();
            mockRememberMeCookie(TEST_SERIES);
            List<AuthRememberedDeviceView> authDevices = List.of(createTestAuthDeviceView(TEST_SERIES, true));
            ApiResponse<List<AuthRememberedDeviceView>> apiResponse = createAuthSuccessResponse(authDevices);

            when(authAccountSecurityInternalClient.listRememberMeTokens(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserActiveSessions(request);

            assertThat(result).hasSize(1);
            verify(authAccountSecurityInternalClient).listRememberMeTokens(TEST_USER_ID, TEST_SERIES);
        }

        @Test
        @DisplayName("当request为null时，activeSeries为null")
        void whenRequestIsNull_shouldPassNullActiveSeries() {
            mockCurrentUser();
            List<AuthRememberedDeviceView> authDevices = List.of(createTestAuthDeviceView(TEST_SERIES, true));
            ApiResponse<List<AuthRememberedDeviceView>> apiResponse = createAuthSuccessResponse(authDevices);

            when(authAccountSecurityInternalClient.listRememberMeTokens(eq(TEST_USER_ID), eq(null))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserActiveSessions(null);

            assertThat(result).hasSize(1);
            verify(authAccountSecurityInternalClient).listRememberMeTokens(TEST_USER_ID, null);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.listRememberMeTokens(eq(TEST_USER_ID), eq(null))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.listCurrentUserActiveSessions(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<List<AuthRememberedDeviceView>> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(authAccountSecurityInternalClient.listRememberMeTokens(eq(TEST_USER_ID), eq(null))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.listCurrentUserActiveSessions(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("查询活跃会话失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.listCurrentUserActiveSessions(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("listCurrentUserSecurityHistory 方法测试")
    class ListCurrentUserSecurityHistoryTests {

        @Test
        @DisplayName("当认证中心返回历史记录时，正确转换并返回")
        void whenAuthServiceReturnsHistory_shouldConvertAndReturn() {
            mockCurrentUser();
            List<AuthSecurityEventView> events = List.of(
                    createTestAuthEventView(1L, "LOGIN", true),
                    createTestAuthEventView(2L, "LOGOUT", true)
            );
            AuthSecurityHistoryPageView historyPage = new AuthSecurityHistoryPageView(events, 0, 10, 2);
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserSecurityHistory(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.items()).hasSize(2);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(2);
            assertThat(result.items().get(0).id()).isEqualTo(1L);
            assertThat(result.items().get(0).type()).isEqualTo("LOGIN");
        }

        @Test
        @DisplayName("当认证中心返回null数据时，返回空历史记录")
        void whenAuthServiceReturnsNullData_shouldReturnEmptyHistory() {
            mockCurrentUser();
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserSecurityHistory(0, 10);

            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        @DisplayName("当认证中心返回null items时，返回空历史记录")
        void whenAuthServiceReturnsNullItems_shouldReturnEmptyHistory() {
            mockCurrentUser();
            AuthSecurityHistoryPageView historyPage = new AuthSecurityHistoryPageView(null, 0, 10, 0);
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSecurityService.listCurrentUserSecurityHistory(0, 10);

            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("当page为负数时，使用0作为默认值")
        void whenPageIsNegative_shouldUseZeroAsDefault() {
            mockCurrentUser();
            AuthSecurityHistoryPageView historyPage = new AuthSecurityHistoryPageView(List.of(), 0, 10, 0);
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            userSecurityService.listCurrentUserSecurityHistory(-1, 10);

            verify(authAccountSecurityInternalClient).listSecurityHistory(TEST_USER_ID, 0, 10);
        }

        @Test
        @DisplayName("当size小于1时，使用1作为默认值")
        void whenSizeIsLessThanOne_shouldUseOneAsDefault() {
            mockCurrentUser();
            AuthSecurityHistoryPageView historyPage = new AuthSecurityHistoryPageView(List.of(), 0, 1, 0);
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(1))).thenReturn(apiResponse);

            userSecurityService.listCurrentUserSecurityHistory(0, 0);

            verify(authAccountSecurityInternalClient).listSecurityHistory(TEST_USER_ID, 0, 1);
        }

        @Test
        @DisplayName("当size超过100时，使用100作为最大值")
        void whenSizeExceeds100_shouldUse100AsMax() {
            mockCurrentUser();
            AuthSecurityHistoryPageView historyPage = new AuthSecurityHistoryPageView(List.of(), 0, 100, 0);
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(100))).thenReturn(apiResponse);

            userSecurityService.listCurrentUserSecurityHistory(0, 200);

            verify(authAccountSecurityInternalClient).listSecurityHistory(TEST_USER_ID, 0, 100);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.listCurrentUserSecurityHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthSecurityHistoryPageView> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(authAccountSecurityInternalClient.listSecurityHistory(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.listCurrentUserSecurityHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("查询安全历史失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.listCurrentUserSecurityHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("revokeCurrentUserSession 方法测试")
    class RevokeCurrentUserSessionTests {

        @Test
        @DisplayName("当series为null时，不执行任何操作")
        void whenSeriesIsNull_shouldDoNothing() {
            mockCurrentUser();
            userSecurityService.revokeCurrentUserSession(null, request, response);

            verify(authAccountSecurityInternalClient, never()).revokeRememberMeToken(any(), any());
        }

        @Test
        @DisplayName("当series为空字符串时，不执行任何操作")
        void whenSeriesIsEmpty_shouldDoNothing() {
            mockCurrentUser();
            userSecurityService.revokeCurrentUserSession("   ", request, response);

            verify(authAccountSecurityInternalClient, never()).revokeRememberMeToken(any(), any());
        }

        @Test
        @DisplayName("当撤销成功时，调用认证中心接口")
        void whenRevokeSuccess_shouldCallAuthService() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.revokeRememberMeToken(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(apiResponse);

            userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response);

            verify(authAccountSecurityInternalClient).revokeRememberMeToken(TEST_USER_ID, TEST_SERIES);
        }

        @Test
        @DisplayName("当撤销的是当前会话时，清除remember-me cookie")
        void whenRevokeCurrentSession_shouldClearCookie() {
            mockCurrentUser();
            mockRememberMeCookie(TEST_SERIES);
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.revokeRememberMeToken(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(apiResponse);

            userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response);

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            Cookie clearedCookie = cookieCaptor.getValue();
            assertThat(clearedCookie.getName()).isEqualTo(REMEMBER_ME_COOKIE_NAME);
            assertThat(clearedCookie.getMaxAge()).isEqualTo(0);
        }

        @Test
        @DisplayName("当撤销的不是当前会话时，不清除cookie")
        void whenRevokeNotCurrentSession_shouldNotClearCookie() {
            mockCurrentUser();
            mockRememberMeCookie("other_series");
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.revokeRememberMeToken(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(apiResponse);

            userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response);

            verify(response, never()).addCookie(any(Cookie.class));
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.revokeRememberMeToken(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "撤销失败");

            when(authAccountSecurityInternalClient.revokeRememberMeToken(eq(TEST_USER_ID), eq(TEST_SERIES))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("注销会话失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.revokeCurrentUserSession(TEST_SERIES, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("revokeAllCurrentUserSessions 方法测试")
    class RevokeAllCurrentUserSessionsTests {

        @Test
        @DisplayName("当撤销全部成功时，调用认证中心接口并清除cookie")
        void whenRevokeAllSuccess_shouldCallAuthServiceAndClearCookie() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.revokeAllRememberMeTokens(eq(TEST_USER_ID))).thenReturn(apiResponse);

            userSecurityService.revokeAllCurrentUserSessions(request, response);

            verify(authAccountSecurityInternalClient).revokeAllRememberMeTokens(TEST_USER_ID);
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            Cookie clearedCookie = cookieCaptor.getValue();
            assertThat(clearedCookie.getName()).isEqualTo(REMEMBER_ME_COOKIE_NAME);
            assertThat(clearedCookie.getMaxAge()).isEqualTo(0);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.revokeAllRememberMeTokens(eq(TEST_USER_ID))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.revokeAllCurrentUserSessions(request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "撤销失败");

            when(authAccountSecurityInternalClient.revokeAllRememberMeTokens(eq(TEST_USER_ID))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.revokeAllCurrentUserSessions(request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("注销全部会话失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.revokeAllCurrentUserSessions(request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("changeCurrentUserPassword 方法测试")
    class ChangeCurrentUserPasswordTests {

        @Test
        @DisplayName("当request为null时，抛出InfraException")
        void whenRequestIsNull_shouldThrowInfraException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(null, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("request body is required");
        }

        @Test
        @DisplayName("当currentPassword为null时，抛出InfraException")
        void whenCurrentPasswordIsNull_shouldThrowInfraException() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest(null, "newPassword123");

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("currentPassword is required");
        }

        @Test
        @DisplayName("当currentPassword为空字符串时，抛出InfraException")
        void whenCurrentPasswordIsEmpty_shouldThrowInfraException() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("   ", "newPassword123");

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("currentPassword is required");
        }

        @Test
        @DisplayName("当newPassword为null时，抛出InfraException")
        void whenNewPasswordIsNull_shouldThrowInfraException() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("oldPassword123", null);

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("newPassword is required");
        }

        @Test
        @DisplayName("当修改密码成功时，调用认证中心接口并清除cookie")
        void whenChangeSuccess_shouldCallAuthServiceAndClearCookie() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("oldPassword123", "newPassword123");
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.changePassword(
                    eq(TEST_USER_ID),
                    any(AuthChangePasswordRequest.class))).thenReturn(apiResponse);

            userSecurityService.changeCurrentUserPassword(passwordRequest, request, response);

            verify(authAccountSecurityInternalClient).changePassword(
                    eq(TEST_USER_ID),
                    any(AuthChangePasswordRequest.class));
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            Cookie clearedCookie = cookieCaptor.getValue();
            assertThat(clearedCookie.getName()).isEqualTo(REMEMBER_ME_COOKIE_NAME);
            assertThat(clearedCookie.getMaxAge()).isEqualTo(0);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("oldPassword123", "newPassword123");

            when(authAccountSecurityInternalClient.changePassword(
                    eq(TEST_USER_ID),
                    any(AuthChangePasswordRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("oldPassword123", "newPassword123");
            ApiResponse<Object> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "修改失败");

            when(authAccountSecurityInternalClient.changePassword(
                    eq(TEST_USER_ID),
                    any(AuthChangePasswordRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("修改密码失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();
            UserChangePasswordRequest passwordRequest = new UserChangePasswordRequest("oldPassword123", "newPassword123");

            assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(passwordRequest, request, response))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("requestCurrentUserBindPhoneOtp 方法测试")
    class RequestCurrentUserBindPhoneOtpTests {

        @Test
        @DisplayName("当phone为null时，抛出BizException")
        void whenPhoneIsNull_shouldThrowBizException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is required");
        }

        @Test
        @DisplayName("当phone为空字符串时，抛出BizException")
        void whenPhoneIsEmpty_shouldThrowBizException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp("   "))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is required");
        }

        @Test
        @DisplayName("当请求成功时，调用认证中心接口")
        void whenRequestSuccess_shouldCallAuthService() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.requestBindPhoneOtp(
                    eq(TEST_USER_ID),
                    any(AuthRequestPhoneBindOtpRequest.class))).thenReturn(apiResponse);

            userSecurityService.requestCurrentUserBindPhoneOtp(TEST_PHONE);

            verify(authAccountSecurityInternalClient).requestBindPhoneOtp(
                    eq(TEST_USER_ID),
                    any(AuthRequestPhoneBindOtpRequest.class));
        }

        @Test
        @DisplayName("当手机号已绑定时，抛出BizException")
        void whenPhoneAlreadyBound_shouldThrowBizException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthErrorResponse("AUTH-1019", "手机号已绑定");

            when(authAccountSecurityInternalClient.requestBindPhoneOtp(
                    eq(TEST_USER_ID),
                    any(AuthRequestPhoneBindOtpRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(TEST_PHONE))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.PHONE_ALREADY_BOUND.getCode());
        }

        @Test
        @DisplayName("当验证码请求过于频繁时，抛出BizException")
        void whenOtpRequestTooFrequent_shouldThrowBizException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthErrorResponse("AUTH-1005", "验证码请求过于频繁");

            when(authAccountSecurityInternalClient.requestBindPhoneOtp(
                    eq(TEST_USER_ID),
                    any(AuthRequestPhoneBindOtpRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(TEST_PHONE))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.OTP_REQUEST_TOO_FREQUENT.getCode());
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.requestBindPhoneOtp(
                    eq(TEST_USER_ID),
                    any(AuthRequestPhoneBindOtpRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(TEST_PHONE))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(TEST_PHONE))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("confirmCurrentUserBindPhone 方法测试")
    class ConfirmCurrentUserBindPhoneTests {

        @Test
        @DisplayName("当phone为null时，抛出BizException")
        void whenPhoneIsNull_shouldThrowBizException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(null, TEST_OTP_CODE))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is required");
        }

        @Test
        @DisplayName("当code为null时，抛出BizException")
        void whenCodeIsNull_shouldThrowBizException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("code is required");
        }

        @Test
        @DisplayName("当确认成功时，调用认证中心接口并更新用户Profile")
        void whenConfirmSuccess_shouldCallAuthServiceAndUpdateProfile() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authAccountSecurityInternalClient.confirmBindPhone(
                    eq(TEST_USER_ID),
                    any(AuthConfirmPhoneBindRequest.class))).thenReturn(apiResponse);

            userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, TEST_OTP_CODE);

            verify(authAccountSecurityInternalClient).confirmBindPhone(
                    eq(TEST_USER_ID),
                    any(AuthConfirmPhoneBindRequest.class));
            verify(userProfileService).updateCurrentUserPhone(TEST_PHONE);
        }

        @Test
        @DisplayName("当验证码无效时，抛出BizException")
        void whenOtpInvalid_shouldThrowBizException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthErrorResponse("AUTH-1003", "验证码无效");

            when(authAccountSecurityInternalClient.confirmBindPhone(
                    eq(TEST_USER_ID),
                    any(AuthConfirmPhoneBindRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, TEST_OTP_CODE))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.OTP_INVALID.getCode());
        }

        @Test
        @DisplayName("当验证码验证过于频繁时，抛出BizException")
        void whenOtpVerifyTooFrequent_shouldThrowBizException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthErrorResponse("AUTH-1015", "验证码验证过于频繁");

            when(authAccountSecurityInternalClient.confirmBindPhone(
                    eq(TEST_USER_ID),
                    any(AuthConfirmPhoneBindRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, TEST_OTP_CODE))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.OTP_VERIFY_TOO_FREQUENT.getCode());
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authAccountSecurityInternalClient.confirmBindPhone(
                    eq(TEST_USER_ID),
                    any(AuthConfirmPhoneBindRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, TEST_OTP_CODE))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(TEST_PHONE, TEST_OTP_CODE))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }
}
