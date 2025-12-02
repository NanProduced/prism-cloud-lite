package nan.produced.prism.auth.security.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 验证令牌服务
 * 负责 OTP 验证通过后生成的临时令牌的存储、验证和删除
 * <p>
 * 使用场景：
 * 1. 用户验证 OTP 成功后，生成验证令牌并存储到 Redis
 * 2. 用户提交完整注册信息时，验证该令牌的有效性
 * 3. 验证成功后删除令牌（一次性使用）
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SecurityProps securityProps;

    /**
     * 存储验证令牌到 Redis
     * @param email 邮箱地址
     * @param token 验证令牌
     * @throws InfraException 如果 Redis 操作失败
     */
    public void storeToken(String email, String token) {
        try {
            String key = buildKey(email);
            long validityMinutes = securityProps.getVerificationToken().getValidityMinutes();

            redisTemplate.opsForValue().set(key, token, validityMinutes, TimeUnit.MINUTES);

            log.info("Verification token stored for email: {}, validity: {} minutes", email, validityMinutes);
        } catch (Exception e) {
            log.error("Failed to store verification token for email: {}", email, e);
            throw new InfraException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "验证令牌存储失败",
                e
            );
        }
    }

    /**
     * 验证并消费令牌（验证成功后删除）
     * @param email 邮箱地址
     * @param token 用户提供的验证令牌
     * @throws BizException 如果令牌不存在、已过期或不匹配
     */
    public void validateAndConsumeToken(String email, String token) {
        String key = buildKey(email);

        try {
            // 从 Redis 获取存储的令牌
            String storedToken = redisTemplate.opsForValue().get(key);

            // 令牌不存在或已过期
            if (storedToken == null) {
                log.warn("Verification token not found or expired for email: {}", email);
                throw new BizException(ErrorCode.VERIFICATION_TOKEN_NOT_FOUND);
            }

            // 令牌不匹配
            if (!storedToken.equals(token)) {
                log.warn("Verification token mismatch for email: {}", email);
                throw new BizException(ErrorCode.VERIFICATION_TOKEN_INVALID);
            }

            // 验证成功，删除令牌（一次性使用）
            redisTemplate.delete(key);
            log.info("Verification token validated and consumed for email: {}", email);

        } catch (BizException e) {
            // 业务异常直接抛出
            throw e;
        } catch (Exception e) {
            // Redis 连接异常等基础设施错误
            log.error("Failed to validate verification token for email: {}", email, e);
            throw new InfraException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "验证令牌校验失败",
                e
            );
        }
    }

    /**
     * 删除验证令牌（用于清理或取消操作）
     * @param email 邮箱地址
     */
    public void deleteToken(String email) {
        try {
            String key = buildKey(email);
            redisTemplate.delete(key);
            log.info("Verification token deleted for email: {}", email);
        } catch (Exception e) {
            // 删除操作失败仅记录日志，不抛出异常
            log.warn("Failed to delete verification token for email: {}", email, e);
        }
    }

    /**
     * 构建 Redis key
     * @param email 邮箱地址
     * @return Redis key: auth:verification_token:{email}
     */
    private String buildKey(String email) {
        String keyPrefix = securityProps.getVerificationToken().getKeyPrefix();
        return keyPrefix + email;
    }
}
