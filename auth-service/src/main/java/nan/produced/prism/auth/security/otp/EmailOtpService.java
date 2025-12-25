package nan.produced.prism.auth.security.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static nan.produced.prism.auth.common.exception.ErrorCode.OTP_REQUEST_TOO_FREQUENT;

/**
 * OTP（一次性密码）服务
 * 负责OTP的生成、存储、验证和防暴力破解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final OtpProps otpProps;

    private static final String RATE_LIMIT_KEY_PREFIX = "auth:otp:rate-limit:";
    private static final String OTP_KEY_PREFIX = "auth:otp:";
    private static final String OTP_ATTEMPT_KEY_PREFIX = "auth:otp:attempt:";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 判断是否可以请求验证码
     * @param email 邮箱地址
     * @return 是否可以请求验证码
     */
    public Boolean canApplyOtp(String email) {
        return canApplyOtp(email, OtpScene.LOGIN);
    }

    /**
     * 判断是否可以请求验证码（按场景隔离）。
     *
     * @param email 邮箱地址
     * @param scene OTP 场景
     * @return 是否可以请求验证码
     */
    public Boolean canApplyOtp(String email, OtpScene scene) {
        String normalized = normalizeIdentifier(email);
        String key = rateLimitKey(scene, normalized);
        return redisTemplate.opsForValue().setIfAbsent(key, "1", otpProps.getRateLimit().getWindowMinutes(), TimeUnit.MINUTES);
    }

    /**
     * 生成并存储OTP
     * @param email 邮箱地址
     * @return 生成的OTP
     */
    public String generateAndStoreOtp(String email) {
        return generateAndStoreOtp(email, OtpScene.LOGIN);
    }

    /**
     * 生成并存储OTP（按场景隔离）。
     *
     * @param email 邮箱地址
     * @param scene OTP 场景
     * @return 生成的OTP
     */
    public String generateAndStoreOtp(String email, OtpScene scene) {
        String normalized = normalizeIdentifier(email);
        if (Boolean.FALSE.equals(canApplyOtp(normalized, scene))) {
            throw new BizException(OTP_REQUEST_TOO_FREQUENT);
        }

        // 生成OTP
        String otp = generateOtp();

        // 存储到Redis
        String key = otpKey(scene, normalized);
        long validityMinutes = otpProps.getValidityMinutes();
        redisTemplate.opsForValue().set(key, otp, validityMinutes, TimeUnit.MINUTES);

        // 重置错误尝试计数
        String attemptKey = attemptKey(scene, normalized);
        redisTemplate.delete(attemptKey);

        log.info("OTP generated and stored for identifier: {}, scene: {}", normalized, scene);
        return otp;
    }

    /**
     * 验证OTP
     * @param email 邮箱地址
     * @param otpCode 用户输入的OTP
     * @throws BizException 验证失败异常
     */
    public void verifyOtp(String email, String otpCode) {
        verifyOtp(email, otpCode, OtpScene.LOGIN);
    }

    /**
     * 验证OTP（按场景隔离）。
     *
     * @param email 邮箱地址
     * @param otpCode 用户输入的OTP
     * @param scene OTP 场景
     */
    public void verifyOtp(String email, String otpCode, OtpScene scene) {
        String normalized = normalizeIdentifier(email);
        // 检查防暴力破解限制
        checkRateLimit(scene, normalized);

        // 从Redis获取OTP
        String key = otpKey(scene, normalized);
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            recordFailedAttempt(scene, normalized);
            throw new BizException(ErrorCode.OTP_EXPIRED);
        }

        if (!storedOtp.equals(otpCode)) {
            recordFailedAttempt(scene, normalized);
            throw new BizException(ErrorCode.INVALID_OTP);
        }

        // 验证成功，删除OTP
        redisTemplate.delete(key);

        // 清除错误尝试记录
        String attemptKey = attemptKey(scene, normalized);
        redisTemplate.delete(attemptKey);

        log.info("OTP verified successfully for identifier: {}, scene: {}", normalized, scene);
    }

    /**
     * 检查防暴力破解限制
     * @param normalized 邮箱地址
     * @throws BizException 超过尝试次数
     */
    private void checkRateLimit(OtpScene scene, String normalized) {
        String attemptKey = attemptKey(scene, normalized);
        String attemptCountStr = redisTemplate.opsForValue().get(attemptKey);

        if (attemptCountStr != null) {
            int attemptCount = Integer.parseInt(attemptCountStr);
            int maxAttempts = otpProps.getRateLimit().getMaxAttempts();
            if (attemptCount >= maxAttempts) {
                long windowMinutes = otpProps.getRateLimit().getWindowMinutes();
                throw new BizException(
                    ErrorCode.OTP_VERIFY_TOO_FREQUENT,
                    String.format("验证失败次数过多，请在%d分钟后重试", windowMinutes)
                );
            }
        }
    }

    /**
     * 记录验证失败的尝试
     * @param normalized 邮箱地址
     */
    private void recordFailedAttempt(OtpScene scene, String normalized) {
        String attemptKey = attemptKey(scene, normalized);
        String attemptCountStr = redisTemplate.opsForValue().get(attemptKey);

        int attemptCount = 1;
        if (attemptCountStr != null) {
            attemptCount = Integer.parseInt(attemptCountStr) + 1;
        }

        long windowMinutes = otpProps.getRateLimit().getWindowMinutes();
        redisTemplate.opsForValue().set(
            attemptKey,
            String.valueOf(attemptCount),
            windowMinutes,
            TimeUnit.MINUTES
        );

        log.warn("Failed OTP verification for identifier: {}, scene: {}, attempt: {}", normalized, scene, attemptCount);
    }

    /**
     * 生成指定长度的随机OTP
     * @return OTP字符串
     */
    private String generateOtp() {
        int length = otpProps.getLength();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    /**
     * 删除OTP（用于过期或手动清理）
     * @param email 邮箱地址
     */
    public void deleteOtp(String email) {
        deleteOtp(email, OtpScene.LOGIN);
    }

    /**
     * 删除OTP（按场景隔离）。
     *
     * @param email 邮箱地址
     * @param scene OTP 场景
     */
    public void deleteOtp(String email, OtpScene scene) {
        String normalized = normalizeIdentifier(email);
        redisTemplate.delete(otpKey(scene, normalized));
        redisTemplate.delete(attemptKey(scene, normalized));
        log.info("OTP deleted for identifier: {}, scene: {}", normalized, scene);
    }

    /**
     * 标准化标识符
     * @param identifier 标识符
     * @return 标准化后的标识符
     */
    private String normalizeIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "目标标识不能为空");
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String rateLimitKey(OtpScene scene, String normalized) {
        return RATE_LIMIT_KEY_PREFIX + normalizeScene(scene) + ":" + normalized;
    }

    private String otpKey(OtpScene scene, String normalized) {
        return OTP_KEY_PREFIX + normalizeScene(scene) + ":" + normalized;
    }

    private String attemptKey(OtpScene scene, String normalized) {
        return OTP_ATTEMPT_KEY_PREFIX + normalizeScene(scene) + ":" + normalized;
    }

    private String normalizeScene(OtpScene scene) {
        return (scene == null ? OtpScene.LOGIN : scene).name().toLowerCase(Locale.ROOT);
    }
}
