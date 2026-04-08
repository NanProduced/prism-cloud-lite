package nan.produced.prism.core.user.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService 单元测试")
class UserProfileServiceTests {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final String TEST_PUBLIC_ID = "test_public_id_1234";
    private static final String TEST_USER_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_DISPLAY_NAME = "Test User";
    private static final String TEST_PHONE = "13800138000";
    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    @BeforeEach
    void setUp() {
        cloudAuthContextMock = mockStatic(CloudAuthContext.class);
    }

    @AfterEach
    void tearDown() {
        cloudAuthContextMock.close();
    }

    private void mockCurrentUser(String publicId, String userUuid, String tier) {
        CloudAuthUser authUser = new CloudAuthUser(publicId, userUuid, null, tier);
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
    }

    private UserProfileEntity createTestProfile() {
        return UserProfileEntity.builder()
                .id(UUID.fromString(TEST_USER_UUID))
                .publicId(TEST_PUBLIC_ID)
                .email(TEST_EMAIL)
                .displayName(TEST_DISPLAY_NAME)
                .subscriptionTier("FREE")
                .metadata(new HashMap<>())
                .configs(new HashMap<>())
                .build();
    }

    private AuthInternalUserResponse createTestAuthResponse() {
        AuthInternalUserResponse response = new AuthInternalUserResponse();
        response.setPublicId(TEST_PUBLIC_ID);
        response.setUserId(TEST_USER_UUID);
        response.setEmail(TEST_EMAIL);
        response.setDisplayName(TEST_DISPLAY_NAME);
        response.setPhone(TEST_PHONE);
        return response;
    }

    private ApiResponse<AuthInternalUserResponse> createAuthSuccessResponse(AuthInternalUserResponse data) {
        return ApiResponse.<AuthInternalUserResponse>builder()
                .code(AUTH_SUCCESS_CODE)
                .message("OK")
                .data(data)
                .build();
    }

    @Nested
    @DisplayName("getOrCreateCurrentUserProfile 方法测试")
    class GetOrCreateCurrentUserProfileTests {

        @Test
        @DisplayName("当用户Profile已存在时，直接返回现有Profile")
        void whenProfileExists_shouldReturnExistingProfile() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result).isSameAs(existingProfile);
            verify(authInternalClient, never()).getUserByPublicId(anyString());
            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当用户Profile不存在时，执行JIT Provisioning创建新Profile")
        void whenProfileNotExists_shouldPerformJitProvisioning() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "PRO");
            AuthInternalUserResponse authResponse = createTestAuthResponse();
            ApiResponse<AuthInternalUserResponse> apiResponse = createAuthSuccessResponse(authResponse);

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result).isNotNull();
            assertThat(result.getPublicId()).isEqualTo(TEST_PUBLIC_ID);
            assertThat(result.getEmail()).isEqualTo(TEST_EMAIL.toLowerCase());
            assertThat(result.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
            assertThat(result.getSubscriptionTier()).isEqualTo("PRO");
            assertThat(result.getPhone()).isEqualTo(TEST_PHONE);

            verify(userQuotaUsageRepository).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当认证中心未返回邮箱时，抛出InfraException")
        void whenAuthServiceReturnsNoEmail_shouldThrowInfraException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            AuthInternalUserResponse authResponse = new AuthInternalUserResponse();
            authResponse.setPublicId(TEST_PUBLIC_ID);
            authResponse.setEmail(null);
            ApiResponse<AuthInternalUserResponse> apiResponse = createAuthSuccessResponse(authResponse);

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUserProfile())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("认证中心未返回邮箱");
        }

        @Test
        @DisplayName("当认证中心返回错误响应时，抛出InfraException")
        void whenAuthServiceReturnsError_shouldThrowInfraException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            ApiResponse<AuthInternalUserResponse> apiResponse = ApiResponse.error(ErrorCode.EXTERNAL_SERVICE_ERROR, "服务不可用");

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUserProfile())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("认证中心返回错误");
        }

        @Test
        @DisplayName("当并发创建Profile时，重试查找并返回已创建的Profile")
        void whenConcurrentCreation_shouldRetryLookup() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            AuthInternalUserResponse authResponse = createTestAuthResponse();
            ApiResponse<AuthInternalUserResponse> apiResponse = createAuthSuccessResponse(authResponse);
            UserProfileEntity existingProfile = createTestProfile();

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingProfile));
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);
            when(userProfileRepository.save(any(UserProfileEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result).isSameAs(existingProfile);
        }

        @Test
        @DisplayName("当并发创建且重试查找失败时，抛出InfraException")
        void whenConcurrentCreationAndRetryFails_shouldThrowInfraException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            AuthInternalUserResponse authResponse = createTestAuthResponse();
            ApiResponse<AuthInternalUserResponse> apiResponse = createAuthSuccessResponse(authResponse);

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);
            when(userProfileRepository.save(any(UserProfileEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUserProfile())
                    .isInstanceOf(InfraException.class)
                    .hasMessageContaining("Profile creation failed after retry");
        }

        @Test
        @DisplayName("当认证中心返回的displayName为空时，生成默认显示名")
        void whenAuthServiceReturnsNoDisplayName_shouldGenerateDefault() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            AuthInternalUserResponse authResponse = new AuthInternalUserResponse();
            authResponse.setPublicId(TEST_PUBLIC_ID);
            authResponse.setUserId(TEST_USER_UUID);
            authResponse.setEmail(TEST_EMAIL);
            authResponse.setDisplayName(null);
            ApiResponse<AuthInternalUserResponse> apiResponse = createAuthSuccessResponse(authResponse);

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.empty());
            when(authInternalClient.getUserByPublicId(TEST_PUBLIC_ID)).thenReturn(apiResponse);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileEntity result = userProfileService.getOrCreateCurrentUserProfile();

            assertThat(result.getDisplayName()).startsWith("User_");
        }
    }

    @Nested
    @DisplayName("saveCurrentUserProfile 方法测试")
    class SaveCurrentUserProfileTests {

        @Test
        @DisplayName("当request为null时，抛出BizException")
        void whenRequestIsNull_shouldThrowBizException() {
            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("request body is required");
        }

        @Test
        @DisplayName("当displayName为空字符串时，抛出BizException")
        void whenDisplayNameIsEmpty_shouldThrowBizException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            UserProfileSaveRequest request = new UserProfileSaveRequest("   ", null);

            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("displayName is required");
        }

        @Test
        @DisplayName("当displayName超过80字符时，抛出BizException")
        void whenDisplayNameTooLong_shouldThrowBizException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            String longName = "a".repeat(81);
            UserProfileSaveRequest request = new UserProfileSaveRequest(longName, null);

            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("displayName must be <= 80 characters");
        }

        @Test
        @DisplayName("当仅更新displayName时，应正确保存")
        void whenUpdatingDisplayName_shouldSaveCorrectly() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            String newDisplayName = "New Display Name";

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileSaveRequest request = new UserProfileSaveRequest(newDisplayName, null);
            UserProfileEntity result = userProfileService.saveCurrentUserProfile(request);

            assertThat(result.getDisplayName()).isEqualTo(newDisplayName);
            verify(userProfileRepository).save(existingProfile);
        }

        @Test
        @DisplayName("当仅更新avatarId时，应正确保存到configs")
        void whenUpdatingAvatarId_shouldSaveToConfigs() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            existingProfile.setConfigs(new HashMap<>());
            String newAvatarId = "avatar_02";

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileSaveRequest request = new UserProfileSaveRequest(null, newAvatarId);
            UserProfileEntity result = userProfileService.saveCurrentUserProfile(request);

            assertThat(result.getConfigs()).isNotNull();
            assertThat(result.getConfigs()).containsKey("settings");
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) result.getConfigs().get("settings");
            assertThat(settings).containsKey("profile");
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) settings.get("profile");
            assertThat(profile).containsEntry("avatarId", newAvatarId);
        }

        @Test
        @DisplayName("当displayName和avatarId都为null时，不执行保存")
        void whenBothFieldsNull_shouldNotSave() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            UserProfileSaveRequest request = new UserProfileSaveRequest(null, null);
            UserProfileEntity result = userProfileService.saveCurrentUserProfile(request);

            assertThat(result).isSameAs(existingProfile);
            verify(userProfileRepository, never()).save(any(UserProfileEntity.class));
        }

        @Test
        @DisplayName("当avatarId为空字符串时，抛出BizException")
        void whenAvatarIdIsEmpty_shouldThrowBizException() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            UserProfileSaveRequest request = new UserProfileSaveRequest(null, "   ");

            assertThatThrownBy(() -> userProfileService.saveCurrentUserProfile(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("avatarId is required");
        }
    }

    @Nested
    @DisplayName("updateCurrentUserPhone 方法测试")
    class UpdateCurrentUserPhoneTests {

        @Test
        @DisplayName("当phone为null时，抛出BizException")
        void whenPhoneIsNull_shouldThrowBizException() {
            assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone(null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is required");
        }

        @Test
        @DisplayName("当phone为空字符串时，抛出BizException")
        void whenPhoneIsEmpty_shouldThrowBizException() {
            assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone("   "))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is required");
        }

        @Test
        @DisplayName("当phone格式无效时，抛出BizException")
        void whenPhoneFormatInvalid_shouldThrowBizException() {
            assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone("1234567890"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is invalid");

            assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone("23800138000"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is invalid");

            assertThatThrownBy(() -> userProfileService.updateCurrentUserPhone("1380013800"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("phone is invalid");
        }

        @Test
        @DisplayName("当phone格式有效时，应正确更新")
        void whenPhoneFormatValid_shouldUpdateCorrectly() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            String validPhone = "13912345678";

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileEntity result = userProfileService.updateCurrentUserPhone(validPhone);

            assertThat(result.getPhone()).isEqualTo(validPhone);
            verify(userProfileRepository).save(existingProfile);
        }

        @Test
        @DisplayName("当phone包含前后空格时，应先trim再验证")
        void whenPhoneHasWhitespace_shouldTrimAndValidate() {
            mockCurrentUser(TEST_PUBLIC_ID, TEST_USER_UUID, "FREE");
            UserProfileEntity existingProfile = createTestProfile();
            String phoneWithSpaces = "  13912345678  ";

            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileEntity result = userProfileService.updateCurrentUserPhone(phoneWithSpaces);

            assertThat(result.getPhone()).isEqualTo("13912345678");
        }
    }

    @Nested
    @DisplayName("findByPublicId 方法测试")
    class FindByPublicIdTests {

        @Test
        @DisplayName("当用户存在时，返回用户Profile")
        void whenUserExists_shouldReturnProfile() {
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            UserProfileEntity result = userProfileService.findByPublicId(TEST_PUBLIC_ID);

            assertThat(result).isSameAs(existingProfile);
        }

        @Test
        @DisplayName("当用户不存在时，返回null")
        void whenUserNotExists_shouldReturnNull() {
            when(userProfileRepository.findByPublicId("non_existent_id")).thenReturn(Optional.empty());

            UserProfileEntity result = userProfileService.findByPublicId("non_existent_id");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("profileExists 方法测试")
    class ProfileExistsTests {

        @Test
        @DisplayName("当用户存在时，返回true")
        void whenUserExists_shouldReturnTrue() {
            UserProfileEntity existingProfile = createTestProfile();
            when(userProfileRepository.findByPublicId(TEST_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));

            boolean result = userProfileService.profileExists(TEST_PUBLIC_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("当用户不存在时，返回false")
        void whenUserNotExists_shouldReturnFalse() {
            when(userProfileRepository.findByPublicId("non_existent_id")).thenReturn(Optional.empty());

            boolean result = userProfileService.profileExists("non_existent_id");

            assertThat(result).isFalse();
        }
    }
}
