package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserResponse;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.dto.UserProfileSaveRequest;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private CloudAuthUser mockAuthUser;
    private AuthInternalUserResponse mockAuthResponse;
    private UserProfileEntity mockProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockAuthUser = new CloudAuthUser("test-public-id", "test-user-uuid", List.of(), "FREE");
        mockAuthResponse = new AuthInternalUserResponse();
        mockAuthResponse.setEmail("test@example.com");
        mockAuthResponse.setDisplayName("Test User");
        mockAuthResponse.setPhone("13800138000");
        mockAuthResponse.setUserId(UUID.randomUUID().toString());
        mockProfile = UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .publicId("test-public-id")
                .email("test@example.com")
                .displayName("Test User")
                .subscriptionTier("FREE")
                .build();
    }

    @Test
    @DisplayName("获取或创建当前用户 profile - 已存在")
    void testGetOrCreateCurrentUserProfile_Existing() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result).isEqualTo(mockProfile);
        }
    }

    @Test
    @DisplayName("获取或创建当前用户 profile - 不存在，创建成功")
    void testGetOrCreateCurrentUserProfile_NotExists_CreateSuccess() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId("test-public-id")).thenReturn(
                    ApiResponse.<AuthInternalUserResponse>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(mockAuthResponse)
                            .build()
            );
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenReturn(mockProfile);
            when(userQuotaUsageRepository.save(any(UserQuotaUsageEntity.class))).thenReturn(mock(UserQuotaUsageEntity.class));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result).isEqualTo(mockProfile);
        }
    }

    @Test
    @DisplayName("获取或创建当前用户 profile - 认证中心未返回邮箱")
    void testGetOrCreateCurrentUserProfile_AuthNoEmail() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.empty());
            AuthInternalUserResponse noEmailResponse = new AuthInternalUserResponse();
            when(authInternalClient.getUserByPublicId("test-public-id")).thenReturn(
                    ApiResponse.<AuthInternalUserResponse>builder()
                            .code("AUTH-0000")
                            .message("success")
                            .data(noEmailResponse)
                            .build()
            );

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUserProfile())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("认证中心未返回邮箱，无法初始化用户");
        }
    }

    @Test
    @DisplayName("获取或创建当前用户 profile - 认证中心返回错误")
    void testGetOrCreateCurrentUserProfile_AuthError() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId("test-public-id")).thenReturn(
                    ApiResponse.<AuthInternalUserResponse>builder()
                            .code("AUTH-9999")
                            .message("error")
                            .data(null)
                            .build()
            );

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUserProfile())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("认证中心返回错误");
        }
    }

    @Test
    @DisplayName("通过 publicId 查找用户 profile - 存在")
    void testFindByPublicId_Exists() {
        when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));

        UserProfileEntity result = userProfileService.findByPublicId("test-public-id");

        assertThat(result).isEqualTo(mockProfile);
    }

    @Test
    @DisplayName("通过 publicId 查找用户 profile - 不存在")
    void testFindByPublicId_NotExists() {
        when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.empty());

        UserProfileEntity result = userProfileService.findByPublicId("test-public-id");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("检查用户 profile 是否存在 - 存在")
    void testProfileExists_Exists() {
        when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));

        boolean result = userProfileService.profileExists("test-public-id");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查用户 profile 是否存在 - 不存在")
    void testProfileExists_NotExists() {
        when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.empty());

        boolean result = userProfileService.profileExists("test-public-id");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("保存当前用户 profile - 成功")
    void testSaveCurrentUserProfile_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenReturn(mockProfile);

            UserProfileSaveRequest request = new UserProfileSaveRequest("New Display Name", "avatar123");
            UserProfileEntity result = userProfileService.saveCurrentUserProfile(request);

            assertThat(result).isEqualTo(mockProfile);
            assertThat(result.getDisplayName()).isEqualTo("New Display Name");
        }
    }

    @Test
    @DisplayName("保存当前用户 profile - 请求为空")
    void testSaveCurrentUserProfile_NullRequest() {
        assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("request body is required");
    }

    @Test
    @DisplayName("保存当前用户 profile - 显示名为空")
    void testSaveCurrentUserProfile_EmptyDisplayName() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));

            UserProfileSaveRequest request = new UserProfileSaveRequest("", "avatar123");
            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("displayName is required");
        }
    }

    @Test
    @DisplayName("保存当前用户 profile - 显示名过长")
    void testSaveCurrentUserProfile_LongDisplayName() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));

            String longDisplayName = "a".repeat(81);
            UserProfileSaveRequest request = new UserProfileSaveRequest(longDisplayName, "avatar123");
            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("displayName must be <= 80 characters");
        }
    }

    @Test
    @DisplayName("更新当前用户手机号 - 成功")
    void testUpdateCurrentUserPhone_Success() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(CloudAuthContext.class)) {
            mockedContext.when(CloudAuthContext::getCurrentUser).thenReturn(mockAuthUser);
            when(userProfileRepository.findByPublicId("test-public-id")).thenReturn(Optional.of(mockProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenReturn(mockProfile);

            UserProfileEntity result = userProfileService.updateCurrentUserPhone("13800138000");

            assertThat(result).isEqualTo(mockProfile);
            assertThat(result.getPhone()).isEqualTo("13800138000");
        }
    }

    @Test
    @DisplayName("更新当前用户手机号 - 为空")
    void testUpdateCurrentUserPhone_Null() {
        assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("phone is required");
    }

    @Test
    @DisplayName("更新当前用户手机号 - 格式无效")
    void testUpdateCurrentUserPhone_InvalidFormat() {
        assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone("1234567890"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("phone is invalid");
    }
}
