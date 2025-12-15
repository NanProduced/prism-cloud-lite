package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserResponse;
import nan.produced.prism.core.security.CloudAuthContext;
import nan.produced.prism.core.security.CloudAuthUser;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户 Profile 业务逻辑，负责首次登录时的 JIT Provisioning。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final UserProfileRepository userProfileRepository;
    private final AuthInternalClient authInternalClient;
    private final UserQuotaUsageRepository userQuotaUsageRepository;

    @Transactional
    public UserProfileEntity getOrCreateCurrentUserProfile() {
        CloudAuthUser authUser = CloudAuthContext.getCurrentUser();
        String publicId = authUser.publicId();

        return userProfileRepository.findByPublicId(publicId)
            .orElseGet(() -> {
                log.info("JIT Provisioning: profile not found, provisioning publicId={}", publicId);
                return createUserProfileJIT(authUser);
            });
    }

    private UserProfileEntity createUserProfileJIT(CloudAuthUser authUser) {
        String publicId = authUser.publicId();
        AuthInternalUserResponse remote = fetchRemoteProfile(publicId);

        String email = remote.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "认证中心未返回邮箱，无法初始化用户");
        }

        UUID userUuid = resolveUserUuid(authUser, remote);
        String displayName = resolveDisplayName(remote.getDisplayName(), publicId);

        try {
            UserProfileEntity profile = UserProfileEntity.builder()
                .id(userUuid)
                .publicId(publicId)
                .email(email.toLowerCase())
                .phone(remote.getPhone())
                .displayName(displayName)
                .subscriptionTier(authUser.tier() != null ? authUser.tier() : "FREE")
                .subscriptionExpiresAt(null)
                .metadata(new HashMap<>())
                .configs(new HashMap<>())
                .build();

            profile = userProfileRepository.save(profile);
            // 创建默认的资源使用情况
            createDefaultQuotaUsage(profile.getId());

            log.info("JIT Provisioning SUCCESS: publicId={}, coreUserId={}, email={}", publicId, profile.getId(), email);
            return profile;
        } catch (DataIntegrityViolationException ex) {
            log.warn("JIT Provisioning concurrent creation detected for publicId={}, retrying lookup", publicId);
            return userProfileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new InfraException(ErrorCode.USER_PROFILE_CREATION_FAILED,
                    "Profile creation failed after retry: " + publicId));
        }
    }

    private void createDefaultQuotaUsage(UUID userId) {
        userQuotaUsageRepository.save(UserQuotaUsageEntity.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .lastUpdated(Instant.now())
                        .build());
    }

    private AuthInternalUserResponse fetchRemoteProfile(String publicId) {
        try {
            ApiResponse<AuthInternalUserResponse> response = authInternalClient.getUserByPublicId(publicId);
            if (response == null) {
                throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "从认证中心获取用户资料失败: null 响应");
            }
            if (!AUTH_SUCCESS_CODE.equals(response.getCode()) || response.getData() == null) {
                throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "认证中心返回错误: " + response.getMessage());
            }
            return response.getData();
        } catch (InfraException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用认证中心失败", ex);
        }
    }

    private UUID resolveUserUuid(CloudAuthUser authUser, AuthInternalUserResponse remote) {
        if (StringUtils.hasText(remote.getUserId())) {
            try {
                return UUID.fromString(remote.getUserId());
            } catch (IllegalArgumentException ignored) {
                log.warn("Invalid userId from auth-service: {}", remote.getUserId());
            }
        }
        if (StringUtils.hasText(authUser.userUuid())) {
            try {
                return UUID.fromString(authUser.userUuid());
            } catch (IllegalArgumentException ignored) {
                log.warn("Invalid userUuid from CLOUD_AUTH: {}", authUser.userUuid());
            }
        }
        return UUID.randomUUID();
    }

    private String resolveDisplayName(String remoteDisplayName, String publicId) {
        if (StringUtils.hasText(remoteDisplayName)) {
            return remoteDisplayName;
        }
        return generateDefaultDisplayName(publicId);
    }

    private String generateDefaultDisplayName(String publicId) {
        String suffix = StringUtils.hasText(publicId) && publicId.length() > 4
            ? publicId.substring(publicId.length() - 4)
            : UUID.randomUUID().toString().substring(0, 4);
        return "User_" + suffix.toUpperCase();
    }

    public UserProfileEntity findByPublicId(String publicId) {
        return userProfileRepository.findByPublicId(publicId).orElse(null);
    }

    public boolean profileExists(String publicId) {
        return userProfileRepository.findByPublicId(publicId).isPresent();
    }
}
