package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthSubscriptionInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionEventView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionHistoryPageView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionRedeemRequest;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionView;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.user.dto.UserSubscriptionRedeemRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("UserSubscriptionService 单元测试")
class UserSubscriptionServiceTests {

    @Mock
    private AuthSubscriptionInternalClient authSubscriptionInternalClient;

    @Mock
    private UserSubscriptionSignalPublisher userSubscriptionSignalPublisher;

    @InjectMocks
    private UserSubscriptionService userSubscriptionService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_PUBLIC_ID = "test_public_id_1234";
    private static final String TEST_TIER_FREE = "FREE";
    private static final String TEST_TIER_PRO = "PRO";
    private static final String TEST_REDEEM_CODE = "PR7F2A3B4C5D6E8F";
    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";
    private static final String AUTH_REDEEM_CODE_INVALID = "AUTH-1030";

    @BeforeEach
    void setUp() {
        cloudAuthContextMock = mockStatic(CloudAuthContext.class);
    }

    @AfterEach
    void tearDown() {
        cloudAuthContextMock.close();
    }

    private void mockCurrentUser() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, TEST_USER_ID.toString(), null, TEST_TIER_FREE);
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private void mockCurrentUserWithInvalidUuid() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, "invalid-uuid", null, TEST_TIER_FREE);
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private void mockCurrentUserWithNullUuid() {
        CloudAuthUser authUser = new CloudAuthUser(TEST_PUBLIC_ID, null, null, TEST_TIER_FREE);
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private AuthSubscriptionView createTestAuthSubscriptionView(String tier, boolean proActive) {
        return new AuthSubscriptionView(
                tier,
                Instant.now().minusSeconds(86400),
                Instant.now().plusSeconds(86400),
                proActive
        );
    }

    private <T> ApiResponse<T> createAuthSuccessResponse(T data) {
        return ApiResponse.<T>builder()
                .code(AUTH_SUCCESS_CODE)
                .message("OK")
                .data(data)
                .build();
    }

    @Nested
    @DisplayName("getCurrentUserSubscription 方法测试")
    class GetCurrentUserSubscriptionTests {

        @Test
        @DisplayName("当认证中心返回成功响应时，返回订阅信息")
        void whenAuthServiceReturnsSuccess_shouldReturnSubscription() {
            mockCurrentUser();
            AuthSubscriptionView authView = createTestAuthSubscriptionView(TEST_TIER_PRO, true);
            ApiResponse<AuthSubscriptionView> apiResponse = createAuthSuccessResponse(authView);

            when(authSubscriptionInternalClient.getCurrent(eq(TEST_USER_ID))).thenReturn(apiResponse);

            var result = userSubscriptionService.getCurrentUserSubscription();

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_PRO);
            assertThat(result.proActive()).isTrue();
            assertThat(result.startAt()).isEqualTo(authView.startAt());
            assertThat(result.endAt()).isEqualTo(authView.endAt());
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNull_shouldThrowInfraException() {
            mockCurrentUser();

            when(authSubscriptionInternalClient.getCurrent(eq(TEST_USER_ID))).thenReturn(null);

            assertThatThrownBy(() -> userSubscriptionService.getCurrentUserSubscription())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthSubscriptionView> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(authSubscriptionInternalClient.getCurrent(eq(TEST_USER_ID))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSubscriptionService.getCurrentUserSubscription())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("查询订阅失败");
        }

        @Test
        @DisplayName("当认证中心返回null数据时，返回默认FREE订阅")
        void whenAuthServiceReturnsNullData_shouldReturnDefaultFreeSubscription() {
            mockCurrentUser();
            AuthSubscriptionView authView = null;
            ApiResponse<AuthSubscriptionView> apiResponse = createAuthSuccessResponse(authView);

            when(authSubscriptionInternalClient.getCurrent(eq(TEST_USER_ID))).thenReturn(apiResponse);

            var result = userSubscriptionService.getCurrentUserSubscription();

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            assertThat(result.proActive()).isFalse();
            assertThat(result.startAt()).isNull();
            assertThat(result.endAt()).isNull();
        }

        @Test
        @DisplayName("当认证中心返回的tier为null时，使用FREE作为默认值")
        void whenAuthServiceReturnsNullTier_shouldUseFreeAsDefault() {
            mockCurrentUser();
            AuthSubscriptionView authView = new AuthSubscriptionView(null, Instant.now(), Instant.now().plusSeconds(86400), true);
            ApiResponse<AuthSubscriptionView> apiResponse = createAuthSuccessResponse(authView);

            when(authSubscriptionInternalClient.getCurrent(eq(TEST_USER_ID))).thenReturn(apiResponse);

            var result = userSubscriptionService.getCurrentUserSubscription();

            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSubscriptionService.getCurrentUserSubscription())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }

        @Test
        @DisplayName("当userUuid格式无效时，抛出InfraException")
        void whenUserUuidIsInvalid_shouldThrowInfraException() {
            mockCurrentUserWithInvalidUuid();

            assertThatThrownBy(() -> userSubscriptionService.getCurrentUserSubscription())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("Invalid userUuid");
        }
    }

    @Nested
    @DisplayName("redeemCurrentUserSubscription 方法测试")
    class RedeemCurrentUserSubscriptionTests {

        @Test
        @DisplayName("当request为null时，抛出InfraException")
        void whenRequestIsNull_shouldThrowInfraException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(null))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("code is required");
        }

        @Test
        @DisplayName("当code为null时，抛出InfraException")
        void whenCodeIsNull_shouldThrowInfraException() {
            mockCurrentUser();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(null);

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("code is required");
        }

        @Test
        @DisplayName("当code为空字符串时，抛出InfraException")
        void whenCodeIsEmpty_shouldThrowInfraException() {
            mockCurrentUser();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest("   ");

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("code is required");
        }

        @Test
        @DisplayName("当兑换成功时，返回订阅信息并发布更新事件")
        void whenRedeemSuccess_shouldReturnSubscriptionAndPublishEvent() {
            mockCurrentUser();
            AuthSubscriptionView authView = createTestAuthSubscriptionView(TEST_TIER_PRO, true);
            ApiResponse<AuthSubscriptionView> apiResponse = createAuthSuccessResponse(authView);
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(TEST_REDEEM_CODE);

            when(authSubscriptionInternalClient.redeem(
                    eq(TEST_USER_ID),
                    any(AuthSubscriptionRedeemRequest.class))).thenReturn(apiResponse);

            var result = userSubscriptionService.redeemCurrentUserSubscription(request);

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_PRO);
            assertThat(result.proActive()).isTrue();

            verify(userSubscriptionSignalPublisher).publishSubscriptionUpdated(
                    eq(TEST_USER_ID),
                    eq(TEST_TIER_PRO),
                    eq(authView.startAt()),
                    eq(authView.endAt()),
                    eq(true)
            );
        }

        @Test
        @DisplayName("当兑换码无效时，抛出BizException")
        void whenRedeemCodeInvalid_shouldThrowBizException() {
            mockCurrentUser();
            ApiResponse<AuthSubscriptionView> apiResponse = ApiResponse.<AuthSubscriptionView>builder()
                    .code(AUTH_REDEEM_CODE_INVALID)
                    .message("兑换码无效")
                    .data(null)
                    .build();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(TEST_REDEEM_CODE);

            when(authSubscriptionInternalClient.redeem(
                    eq(TEST_USER_ID),
                    any(AuthSubscriptionRedeemRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID.getCode());

            verify(userSubscriptionSignalPublisher, never()).publishSubscriptionUpdated(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNull_shouldThrowInfraException() {
            mockCurrentUser();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(TEST_REDEEM_CODE);

            when(authSubscriptionInternalClient.redeem(
                    eq(TEST_USER_ID),
                    any(AuthSubscriptionRedeemRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回其他错误时，抛出InfraException")
        void whenAuthServiceReturnsOtherError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthSubscriptionView> apiResponse = ApiResponse.<AuthSubscriptionView>builder()
                    .code("AUTH-9999")
                    .message("其他错误")
                    .data(null)
                    .build();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(TEST_REDEEM_CODE);

            when(authSubscriptionInternalClient.redeem(
                    eq(TEST_USER_ID),
                    any(AuthSubscriptionRedeemRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("兑换订阅失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();
            UserSubscriptionRedeemRequest request = new UserSubscriptionRedeemRequest(TEST_REDEEM_CODE);

            assertThatThrownBy(() -> userSubscriptionService.redeemCurrentUserSubscription(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("listCurrentUserSubscriptionHistory 方法测试")
    class ListCurrentUserSubscriptionHistoryTests {

        @Test
        @DisplayName("当认证中心返回成功响应时，返回历史记录")
        void whenAuthServiceReturnsSuccess_shouldReturnHistory() {
            mockCurrentUser();
            Instant now = Instant.now();
            List<AuthSubscriptionEventView> eventViews = List.of(
                    new AuthSubscriptionEventView(1L, "REDEEM_CODE", true, TEST_REDEEM_CODE, "{\"tier\":\"PRO\"}", now),
                    new AuthSubscriptionEventView(2L, "SUBSCRIPTION_EXPIRED", false, null, null, now.minusSeconds(86400))
            );
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(eventViews, 0, 10, 2);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.items()).hasSize(2);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(2);

            assertThat(result.items().get(0).id()).isEqualTo(1L);
            assertThat(result.items().get(0).type()).isEqualTo("REDEEM_CODE");
            assertThat(result.items().get(0).success()).isTrue();
            assertThat(result.items().get(0).code()).isEqualTo(TEST_REDEEM_CODE);
            assertThat(result.items().get(0).createdAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNull_shouldThrowInfraException() {
            mockCurrentUser();

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(null);

            assertThatThrownBy(() -> userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("查询订阅审计失败");
        }

        @Test
        @DisplayName("当认证中心返回null数据时，返回空历史记录")
        void whenAuthServiceReturnsNullData_shouldReturnEmptyHistory() {
            mockCurrentUser();
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(null);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(0);
        }

        @Test
        @DisplayName("当认证中心返回null items时，返回空历史记录")
        void whenAuthServiceReturnsNullItems_shouldReturnEmptyHistory() {
            mockCurrentUser();
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(null, 0, 10, 0);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            var result = userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10);

            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("当page为负数时，使用0作为默认值")
        void whenPageIsNegative_shouldUseZeroAsDefault() {
            mockCurrentUser();
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(List.of(), 0, 10, 0);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(10))).thenReturn(apiResponse);

            userSubscriptionService.listCurrentUserSubscriptionHistory(-1, 10);

            verify(authSubscriptionInternalClient).listEvents(TEST_USER_ID, 0, 10);
        }

        @Test
        @DisplayName("当size小于1时，使用1作为默认值")
        void whenSizeIsLessThanOne_shouldUseOneAsDefault() {
            mockCurrentUser();
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(List.of(), 0, 1, 0);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(1))).thenReturn(apiResponse);

            userSubscriptionService.listCurrentUserSubscriptionHistory(0, 0);

            verify(authSubscriptionInternalClient).listEvents(TEST_USER_ID, 0, 1);
            verify(authSubscriptionInternalClient, never()).listEvents(eq(TEST_USER_ID), eq(0), eq(0));
        }

        @Test
        @DisplayName("当size为负数时，使用1作为默认值")
        void whenSizeIsNegative_shouldUseOneAsDefault() {
            mockCurrentUser();
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(List.of(), 0, 1, 0);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(1))).thenReturn(apiResponse);

            userSubscriptionService.listCurrentUserSubscriptionHistory(0, -5);

            verify(authSubscriptionInternalClient).listEvents(TEST_USER_ID, 0, 1);
            verify(authSubscriptionInternalClient, never()).listEvents(eq(TEST_USER_ID), eq(0), eq(-5));
        }

        @Test
        @DisplayName("当size超过100时，使用100作为最大值")
        void whenSizeExceeds100_shouldUse100AsMax() {
            mockCurrentUser();
            AuthSubscriptionHistoryPageView historyPage = new AuthSubscriptionHistoryPageView(List.of(), 0, 100, 0);
            ApiResponse<AuthSubscriptionHistoryPageView> apiResponse = createAuthSuccessResponse(historyPage);

            when(authSubscriptionInternalClient.listEvents(eq(TEST_USER_ID), eq(0), eq(100))).thenReturn(apiResponse);

            userSubscriptionService.listCurrentUserSubscriptionHistory(0, 200);

            verify(authSubscriptionInternalClient).listEvents(TEST_USER_ID, 0, 100);
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userSubscriptionService.listCurrentUserSubscriptionHistory(0, 10))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }
}
