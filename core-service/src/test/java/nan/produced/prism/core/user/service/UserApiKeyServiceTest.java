package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import nan.produced.prism.core.user.dto.UserApiKeyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserApiKeyServiceTest {

    @Mock
    private AuthApiKeyInternalClient authApiKeyInternalClient;

    @InjectMocks
    private UserApiKeyService userApiKeyService;

    private CloudAuthUser mockAuthUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        mockAuthUser = new CloudAuthUser("test-public-id", userId.toString(), List.of(), "FREE");
    }

    @Test
    @DisplayName("获取当前用户 API Keys - 成功")
    void testListCurrentUserApiKeys_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);

            AuthApiKeyView mockKey = new AuthApiKeyView(
                    "key1", "Test Key", "client1", "secret1", Instant.now(), Instant.now()
            );
            List<AuthApiKeyView> mockKeys = List.of(mockKey);

            when(authApiKeyInternalClient.listApiKeys(userId))
                    .thenReturn(ApiResponse.<List<AuthApiKeyView>>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockKeys)
                            .build());

            List<UserApiKeyView> result = userApiKeyService.listCurrentUserApiKeys();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("key1");
            assertThat(result.get(0).name()).isEqualTo("Test Key");
        }
    }

    @Test
    @DisplayName("获取当前用户 API Keys - 为空")
    void testListCurrentUserApiKeys_Empty() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);

            when(authApiKeyInternalClient.listApiKeys(userId))
                    .thenReturn(ApiResponse.<List<AuthApiKeyView>>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            List<UserApiKeyView> result = userApiKeyService.listCurrentUserApiKeys();

            assertThat(result).isEmpty();
        }
    }

    @Test
    @DisplayName("创建当前用户 API Key - 成功")
    void testCreateCurrentUserApiKey_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);

            AuthApiKeyView mockKey = new AuthApiKeyView(
                    "key1", "Test Key", "client1", "secret1", Instant.now(), null
            );

            when(authApiKeyInternalClient.createApiKey(eq(userId), any(AuthApiKeyCreateRequest.class)))
                    .thenReturn(ApiResponse.<AuthApiKeyView>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockKey)
                            .build());

            UserApiKeyCreateRequest request = new UserApiKeyCreateRequest("Test Key");
            UserApiKeyView result = userApiKeyService.createCurrentUserApiKey(request);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("key1");
            assertThat(result.name()).isEqualTo("Test Key");
        }
    }

    @Test
    @DisplayName("创建当前用户 API Key - 请求为空")
    void testCreateCurrentUserApiKey_NullRequest() {
        assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(null))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("创建当前用户 API Key - 名称为空")
    void testCreateCurrentUserApiKey_EmptyName() {
        UserApiKeyCreateRequest request = new UserApiKeyCreateRequest("");
        assertThatThrownBy(() -> userApiKeyService.createCurrentUserApiKey(request))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    @DisplayName("重置当前用户 API Key 密钥 - 成功")
    void testRegenerateCurrentUserApiKeySecret_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);

            AuthApiKeyView mockKey = new AuthApiKeyView(
                    "key1", "Test Key", "client1", "new-secret", Instant.now(), Instant.now()
            );

            when(authApiKeyInternalClient.regenerateSecret(userId, "key1"))
                    .thenReturn(ApiResponse.<AuthApiKeyView>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockKey)
                            .build());

            UserApiKeyView result = userApiKeyService.regenerateCurrentUserApiKeySecret("key1");

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("key1");
            assertThat(result.clientSecret()).isEqualTo("new-secret");
        }
    }

    @Test
    @DisplayName("重置当前用户 API Key 密钥 - ID为空")
    void testRegenerateCurrentUserApiKeySecret_NullId() {
        assertThatThrownBy(() -> userApiKeyService.regenerateCurrentUserApiKeySecret(null))
                .isInstanceOf(InfraException.class)
                .hasMessageContaining("apiKeyId is required");
    }

    @Test
    @DisplayName("撤销当前用户 API Key - 成功")
    void testRevokeCurrentUserApiKey_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);

            when(authApiKeyInternalClient.revokeApiKey(userId, "key1"))
                    .thenReturn(ApiResponse.<Object>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(null)
                            .build());

            userApiKeyService.revokeCurrentUserApiKey("key1");
        }
    }

    @Test
    @DisplayName("撤销当前用户 API Key - ID为空")
    void testRevokeCurrentUserApiKey_NullId() {
        userApiKeyService.revokeCurrentUserApiKey(null);
        // 应该正常执行，不抛出异常
    }
}
