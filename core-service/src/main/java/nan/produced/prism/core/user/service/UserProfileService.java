package nan.produced.prism.core.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.security.CloudAuthContext;
import nan.produced.prism.core.security.CloudAuthUser;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 用户资料服务
 * 负责 JIT (Just-In-Time) Provisioning：首次访问时自动创建用户资料
 * <p>
 * JIT Provisioning 模式说明:
 * 1. 用户在 Auth-Service 注册时，只创建认证账户
 * 2. 用户首次登录访问业务功能时，Core-Service 自动创建用户资料
 * 3. 使用数据库 UNIQUE(public_id) 约束防止并发重复创建
 * 4. 后续访问直接查询已有资料，性能高效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /**
     * JIT Provisioning: 获取或创建当前用户的资料
     * <p>
     * 首次调用时创建用户资料，后续调用直接返回已有资料
     * 使用数据库 UNIQUE 约束防止并发竞态条件
     *
     * @return 用户资料实体
     * @throws IllegalStateException 如果没有认证用户
     */
    @Transactional
    public UserProfileEntity getOrCreateCurrentUserProfile() {
        CloudAuthUser authUser = CloudAuthContext.getCurrentUser();
        String publicId = authUser.publicId();

        log.debug("JIT Provisioning: checking profile for publicId={}", publicId);

        // 1. 尝试查找已有资料
        return userProfileRepository.findByPublicId(publicId)
            .orElseGet(() -> {
                log.info("JIT Provisioning: No existing profile found, creating new profile for publicId={}", publicId);
                return createUserProfileJIT(authUser);
            });
    }

    /**
     * JIT 创建用户资料
     * <p>
     * 并发安全机制:
     * - 数据库有 UNIQUE(public_id) 约束
     * - 如果并发插入，第二个请求会抛出 DataIntegrityViolationException
     * - 捕获异常后重新查询，返回第一个请求创建的资料
     *
     * @param authUser 认证用户信息（从 CLOUD_AUTH 头解析）
     * @return 新创建的用户资料
     */
    private UserProfileEntity createUserProfileJIT(CloudAuthUser authUser) {
        String publicId = authUser.publicId();

        log.info("JIT Provisioning: Creating user profile for publicId={}", publicId);

        try {
            // 构建用户资料
            UserProfileEntity profile = UserProfileEntity.builder()
                .id(UUID.fromString(authUser.userUuid()))  // 新 UUID
                .publicId(publicId)     // 从 Auth-Service 获取
                .email(null)            // 暂时为空，后续可通过 API 更新
                .phone(null)
                .displayName(generateDefaultDisplayName())  // 默认昵称
                .subscriptionTier(authUser.tier() != null ? authUser.tier() : "FREE")
                .subscriptionExpiresAt(null)
                .build();

            // 保存到数据库
            profile = userProfileRepository.save(profile);

            log.info("JIT Provisioning SUCCESS: publicId={}, coreUserId={}, displayName={}",
                publicId, profile.getId(), profile.getDisplayName());

            return profile;

        } catch (DataIntegrityViolationException e) {
            // 并发情况：其他线程已创建，重新查询
            log.warn("JIT Provisioning: Concurrent creation detected for publicId={}, retrying query...", publicId);

            return userProfileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new InfraException(
                    ErrorCode.USER_PROFILE_CREATION_FAILED,
                    "User profile creation failed even after concurrent retry: " + publicId));
        }
    }

    /**
     * 生成默认显示名称
     * <p>
     * 策略: "用户" + 8位随机字符串（UUID前8位）
     * <p>
     * 示例:
     * - publicId="u_2Xk9P7qL" → displayName="用户a3f5b2c9"
     * - 用户可以在后续通过 API 修改为自己喜欢的昵称
     *
     * @return 默认显示名称
     */
    private String generateDefaultDisplayName() {
        // 使用 UUID 的前 8 位作为随机字符串
        String randomStr = UUID.randomUUID().toString().substring(0, 8);
        return "User_" + randomStr;
    }

    /**
     * 根据 publicId 查找用户资料
     * <p>
     * 注意: 此方法不会触发 JIT 创建，只查询已有资料
     *
     * @param publicId 用户公开 ID
     * @return 用户资料（如果存在），否则返回 null
     */
    public UserProfileEntity findByPublicId(String publicId) {
        return userProfileRepository.findByPublicId(publicId)
            .orElse(null);
    }

    /**
     * 检查用户资料是否已存在
     *
     * @param publicId 用户公开 ID
     * @return true 如果资料已存在
     */
    public boolean profileExists(String publicId) {
        return userProfileRepository.findByPublicId(publicId).isPresent();
    }
}
