package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthApiKeyInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyView;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.user.dto.UserApiKeyCreateRequest;
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
@DisplayName("UserApiKeyService 单元测试")
class UserApiKeyServiceTests {

    @Mock
    private AuthApiKeyInternalClient authApiKeyInternalClient;

    @InjectMocks
    private UserApiKeyService userApiKeyService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_PUBLIC_ID = "test_public_id_1234";
    private static final String TEST_API_KEY_ID = "api_key_001";
    private static final String TEST_API_KEY_NAME = "Test API Key";
    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

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

    private AuthApiKeyView createTestAuthApiKeyView(String id, String name) {
        return new AuthApiKeyView(
                id,
                name,
                "client_id_" + id,
                "client_secret_" + id,
                Instant.now(),
                Instant.now().minusSeconds(3600)
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
    @DisplayName("listCurrentUserApiKeys 方法测试")
    class ListCurrentUserApiKeysTests {

        @Test
        @DisplayName("当认证中心返回API Keys列表时，正确转换并返回")
        void whenAuthServiceReturnsKeys_shouldConvertAndReturn() {
            mockCurrentUser();
            List<AuthApiKeyView> authKeys = List.of(
                    createTestAuthApiKeyView("key_1", "Key One"),
                    createTestAuthApiKeyView("key_2", "Key Two")
            );
            ApiResponse<List<AuthApiKeyView>> apiResponse = createAuthSuccessResponse(authKeys);

            when(authApiKeyInternalClient.listApiKeys(eq(TEST_USER_ID))).thenReturn(apiResponse);

            var result = userApiKeyService.listCurrentUserApiKeys();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo("key_1");
            assertThat(result.get(0).name()).isEqualTo("Key One");
            assertThat(result.get(1).id()).isEqualTo("key_2");
            assertThat(result.get(1).name()).isEqualTo("Key Two");
        }

        @Test
        @DisplayName("当认证中心返回null数据时，返回空列表")
        void whenAuthServiceReturnsNullData_shouldReturnEmptyList() {
            mockCurrentUser();
            ApiResponse<List<AuthApiKeyView>> apiResponse = createAuthSuccessResponse(null);

            when(authApiKeyInternalClient.listApiKeys(eq(TEST_USER_ID))).thenReturn(apiResponse);

            var result = userApiKeyService.listCurrentUserApiKeys();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authApiKeyInternalClient.listApiKeys(eq(TEST_USER_ID))).thenReturn(null);

            assertThatThrownBy(() -> userApiKeyService.listCurrentUserApiKeys())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<List<AuthApiKeyView>> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(authApiKeyInternalClient.listApiKeys(eq(TEST_USER_ID))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userApiKeyService.listCurrentUserApiKeys())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("查询 API Keys 失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userApiKeyService.listCurrentUserApiKeys())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }

        @Test
        @DisplayName("当userUuid格式无效时，抛出InfraException")
        void whenUserUuidIsInvalid_shouldThrowInfraException() {
            mockCurrentUserWithInvalidUuid();

            assertThatThrownBy(() -> userApiKeyService.listCurrentUserApiKeys())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("Invalid userUuid");
        }
    }

    @Nested
    @DisplayName("createCurrentUserApiKey 方法测试")
    class CreateCurrentUserApiKeyTests {

        @Test
        @DisplayName("当request为null时，抛出InfraException")
        void whenRequestIsNull_shouldThrowInfraException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(null))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("当name为null时，抛出InfraException")
        void whenNameIsNull_shouldThrowInfraException() {
            mockCurrentUser();
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest(null);

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("当name为空字符串时，抛出InfraException")
        void whenNameIsEmpty_shouldThrowInfraException() {
            mockCurrentUser();
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest("   ");

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("当创建成功时，返回创建的API Key")
        void whenCreateSuccess_shouldReturnCreatedKey() {
            mockCurrentUser();
            AuthApiKeyView authKey = createTestAuthApiKeyView(TEST_API_KEY_ID, TEST_API_KEY_NAME);
            ApiResponse<AuthApiKeyView> apiResponse = createAuthSuccessResponse(authKey);
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest(TEST_API_KEY_NAME);

            when(authApiKeyInternalClient.createApiKey(
                    eq(TEST_USER_ID),
                    any(AuthApiKeyCreateRequest.class))).thenReturn(apiResponse);

            var result = userApiKeyService.createCurrentUserApiKey(request);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(TEST_API_KEY_ID);
            assertThat(result.name()).isEqualTo(TEST_API_KEY_NAME);
            assertThat(result.clientSecret()).isEqualTo("client_secret_" + TEST_API_KEY_ID);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest(TEST_API_KEY_NAME);

            when(authApiKeyInternalClient.createApiKey(
                    eq(TEST_USER_ID),
                    any(AuthApiKeyCreateRequest.class))).thenReturn(null);

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthApiKeyView> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "创建失败");
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest(TEST_API_KEY_NAME);

            when(authApiKeyInternalClient.createApiKey(
                    eq(TEST_USER_ID),
                    any(AuthApiKeyCreateRequest.class))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("创建 API Key 失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();
            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest(TEST_API_KEY_NAME);

            assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("regenerateCurrentUserApiKeySecret 方法测试")
    class RegenerateCurrentUserApiKeySecretTests {

        @Test
        @DisplayName("当apiKeyId为null时，抛出InfraException")
        void whenApiKeyIdIsNull_shouldThrowInfraException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret(null))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("apiKeyId is required");
        }

        @Test
        @DisplayName("当apiKeyId为空字符串时，抛出InfraException")
        void whenApiKeyIdIsEmpty_shouldThrowInfraException() {
            mockCurrentUser();

            assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret("   "))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("apiKeyId is required");
        }

        @Test
        @DisplayName("当重置成功时，返回更新的API Key")
        void whenRegenerateSuccess_shouldReturnUpdatedKey() {
            mockCurrentUser();
            AuthApiKeyView authKey = createTestAuthApiKeyView(TEST_API_KEY_ID, TEST_API_KEY_NAME);
            ApiResponse<AuthApiKeyView> apiResponse = createAuthSuccessResponse(authKey);

            when(authApiKeyInternalClient.regenerateSecret(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(apiResponse);

            var result = userApiKeyService.regenerateCurrentUserApiKeySecret(TEST_API_KEY_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(TEST_API_KEY_ID);
            assertThat(result.clientSecret()).isEqualTo("client_secret_" + TEST_API_KEY_ID);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authApiKeyInternalClient.regenerateSecret(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(null);

            assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<AuthApiKeyView> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "重置失败");

            when(authApiKeyInternalClient.regenerateSecret(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("重置 API Key 失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }

    @Nested
    @DisplayName("revokeCurrentUserApiKey 方法测试")
    class RevokeCurrentUserApiKeyTests {

        @Test
        @DisplayName("当apiKeyId为null时，不执行任何操作")
        void whenApiKeyIdIsNull_shouldDoNothing() {
            userApiKeyService.revokeCurrentUserApiKey(null);

            verify(authApiKeyInternalClient, never()).revokeApiKey(any(), any());
        }

        @Test
        @DisplayName("当apiKeyId为空字符串时，不执行任何操作")
        void whenApiKeyIdIsEmpty_shouldDoNothing() {
            userApiKeyService.revokeCurrentUserApiKey("   ");

            verify(authApiKeyInternalClient, never()).revokeApiKey(any(), any());
        }

        @Test
        @DisplayName("当撤销成功时，调用认证中心接口")
        void whenRevokeSuccess_shouldCallAuthService() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = createAuthSuccessResponse(null);

            when(authApiKeyInternalClient.revokeApiKey(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(apiResponse);

            userApiKeyService.revokeCurrentUserApiKey(TEST_API_KEY_ID);

            verify(authApiKeyInternalClient).revokeApiKey(TEST_USER_ID, TEST_API_KEY_ID);
        }

        @Test
        @DisplayName("当认证中心返回null响应时，抛出InfraException")
        void whenAuthServiceReturnsNullResponse_shouldThrowInfraException() {
            mockCurrentUser();

            when(authApiKeyInternalClient.revokeApiKey(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(null);

            assertThatThrownBy(() -> userApiKeyService.revokeCurrentUserApiKey(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("null response");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser();
            ApiResponse<Object> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "撤销失败");

            when(authApiKeyInternalClient.revokeApiKey(
                    eq(TEST_USER_ID),
                    eq(TEST_API_KEY_ID))).thenReturn(apiResponse);

            assertThatThrownBy(() -> userApiKeyService.revokeCurrentUserApiKey(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("撤销 API Key 失败");
        }

        @Test
        @DisplayName("当userUuid为null时，抛出InfraException")
        void whenUserUuidIsNull_shouldThrowInfraException() {
            mockCurrentUserWithNullUuid();

            assertThatThrownBy(() -> userApiKeyService.revokeCurrentUserApiKey(TEST_API_KEY_ID))
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("userUuid is missing");
        }
    }
}
