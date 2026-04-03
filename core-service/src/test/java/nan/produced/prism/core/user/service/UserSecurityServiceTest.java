package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.user.dto.UserActiveSessionView;
import nan.produced.prism.core.user.dto.UserChangePasswordRequest;
import nan.produced.prism.core.user.dto.UserSecurityHistoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class UserSecurityServiceTest {

    @Mock
    private AuthAccountSecurityInternalClient authAccountSecurityInternalClient;

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private UserSecurityService userSecurityService;

    private CloudAuthUser mockAuthUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        mockAuthUser = new CloudAuthUser("test-public-id", userId.toString(), List.of(), "FREE");
    }

    @Test
    @DisplayName("获取当前用户活跃会话")
    void testListCurrentUserActiveSessions() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            AuthRememberedDeviceView mockDevice = new AuthRememberedDeviceView(
                    "series1", "Device 1", "127.0.0.1", "Mozilla", null, null, null, true
            );
            List<AuthRememberedDeviceView> mockDevices = List.of(mockDevice);
            when(authAccountSecurityInternalClient.listRememberMeTokens(userId, null))
                    .thenReturn(ApiResponse.<List<AuthRememberedDeviceView>>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockDevices)
                            .build());

            HttpServletRequest request = mock(HttpServletRequest.class);
            List<UserActiveSessionView> result = userSecurityService.listCurrentUserActiveSessions(request);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).series()).isEqualTo("series1");
        }
    }

    @Test
    @DisplayName("获取当前用户安全历史")
    void testListCurrentUserSecurityHistory() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            List<nan.produced.prism.core.integration.auth.dto.AuthSecurityEventView> mockItems = List.of();
            AuthSecurityHistoryPageView mockHistory = new AuthSecurityHistoryPageView(
                    mockItems, 0, 10, 0
            );
            when(authAccountSecurityInternalClient.listSecurityHistory(userId, 0, 10))
                    .thenReturn(ApiResponse.<AuthSecurityHistoryPageView>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockHistory)
                            .build());

            UserSecurityHistoryView result = userSecurityService.listCurrentUserSecurityHistory(0, 10);

            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("注销当前用户会话")
    void testRevokeCurrentUserSession() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            when(authAccountSecurityInternalClient.revokeRememberMeToken(userId, "series1"))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            
            userSecurityService.revokeCurrentUserSession("series1", request, response);
        }
    }

    @Test
    @DisplayName("注销当前用户全部会话")
    void testRevokeAllCurrentUserSessions() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            when(authAccountSecurityInternalClient.revokeAllRememberMeTokens(userId))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            
            userSecurityService.revokeAllCurrentUserSessions(request, response);
        }
    }

    @Test
    @DisplayName("修改当前用户密码 - 成功")
    void testChangeCurrentUserPassword_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            when(authAccountSecurityInternalClient.changePassword(eq(userId), any(AuthChangePasswordRequest.class)))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            UserChangePasswordRequest request = new UserChangePasswordRequest("oldPassword", "newPassword");
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            HttpServletResponse httpResponse = mock(HttpServletResponse.class);
            
            userSecurityService.changeCurrentUserPassword(request, httpRequest, httpResponse);
        }
    }

    @Test
    @DisplayName("修改当前用户密码 - 请求为空")
    void testChangeCurrentUserPassword_NullRequest() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        
        assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(null, httpRequest, httpResponse))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("request body is required");
    }

    @Test
    @DisplayName("修改当前用户密码 - 当前密码为空")
    void testChangeCurrentUserPassword_EmptyCurrentPassword() {
        UserChangePasswordRequest request = new UserChangePasswordRequest("", "newPassword");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        
        assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(request, httpRequest, httpResponse))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("currentPassword is required");
    }

    @Test
    @DisplayName("修改当前用户密码 - 新密码为空")
    void testChangeCurrentUserPassword_EmptyNewPassword() {
        UserChangePasswordRequest request = new UserChangePasswordRequest("oldPassword", "");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        
        assertThatThrownBy(() -> userSecurityService.changeCurrentUserPassword(request, httpRequest, httpResponse))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("newPassword is required");
    }

    @Test
    @DisplayName("请求绑定手机号验证码 - 成功")
    void testRequestCurrentUserBindPhoneOtp_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            when(authAccountSecurityInternalClient.requestBindPhoneOtp(eq(userId), any(AuthRequestPhoneBindOtpRequest.class)))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            userSecurityService.requestCurrentUserBindPhoneOtp("13800138000");
        }
    }

    @Test
    @DisplayName("请求绑定手机号验证码 - 手机号为空")
    void testRequestCurrentUserBindPhoneOtp_NullPhone() {
        assertThatThrownBy(() -> userSecurityService.requestCurrentUserBindPhoneOtp(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("phone is required");
    }

    @Test
    @DisplayName("确认绑定手机号 - 成功")
    void testConfirmCurrentUserBindPhone_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            
            when(authAccountSecurityInternalClient.confirmBindPhone(eq(userId), any(AuthConfirmPhoneBindRequest.class)))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            userSecurityService.confirmCurrentUserBindPhone("13800138000", "123456");
        }
    }

    @Test
    @DisplayName("确认绑定手机号 - 手机号为空")
    void testConfirmCurrentUserBindPhone_NullPhone() {
        assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone(null, "123456"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("phone is required");
    }

    @Test
    @DisplayName("确认绑定手机号 - 验证码为空")
    void testConfirmCurrentUserBindPhone_NullCode() {
        assertThatThrownBy(() -> userSecurityService.confirmCurrentUserBindPhone("13800138000", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("code is required");
    }
}
