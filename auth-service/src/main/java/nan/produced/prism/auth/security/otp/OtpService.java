package nan.produced.prism.auth.security.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * OTP（一次性密码）服务
 * 负责OTP的生成、存储、验证和防暴力破解
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final RedisTemplate<String, String> redisTemplate;
    private final OtpProps otpProps;

    private static final String OTP_KEY_PREFIX = "auth:otp:";
    private static final String OTP_ATTEMPT_KEY_PREFIX = "auth:otp:attempt:";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成并存储OTP
     * @param email 邮箱地址
     * @return 生成的OTP
     */
    public String generateAndStoreOtp(String email) {
        // 生成OTP
        String otp = generateOtp();

        // 存储到Redis
        String key = OTP_KEY_PREFIX + email;
        long validityMinutes = otpProps.getValidityMinutes();
        redisTemplate.opsForValue().set(key, otp, validityMinutes, TimeUnit.MINUTES);

        // 重置错误尝试计数
        String attemptKey = OTP_ATTEMPT_KEY_PREFIX + email;
        redisTemplate.delete(attemptKey);

        log.info("OTP generated and stored for email: {}", email);
        return otp;
    }

    /**
     * 验证OTP
     * @param email 邮箱地址
     * @param otpCode 用户输入的OTP
     * @throws BizException 验证失败异常
     */
    public void verifyOtp(String email, String otpCode) {
        // 检查防暴力破解限制
        checkRateLimit(email);

        // 从Redis获取OTP
        String key = OTP_KEY_PREFIX + email;
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            recordFailedAttempt(email);
            throw new BizException(ErrorCode.OTP_EXPIRED);
        }

        if (!storedOtp.equals(otpCode)) {
            recordFailedAttempt(email);
            throw new BizException(ErrorCode.INVALID_OTP);
        }

        // 验证成功，删除OTP
        redisTemplate.delete(key);

        // 清除错误尝试记录
        String attemptKey = OTP_ATTEMPT_KEY_PREFIX + email;
        redisTemplate.delete(attemptKey);

        log.info("OTP verified successfully for email: {}", email);
    }

    /**
     * 检查防暴力破解限制
     * @param email 邮箱地址
     * @throws BizException 超过尝试次数
     */
    private void checkRateLimit(String email) {
        String attemptKey = OTP_ATTEMPT_KEY_PREFIX + email;
        String attemptCountStr = redisTemplate.opsForValue().get(attemptKey);

        if (attemptCountStr != null) {
            int attemptCount = Integer.parseInt(attemptCountStr);
            int maxAttempts = otpProps.getRateLimit().getMaxAttempts();
            if (attemptCount >= maxAttempts) {
                long windowMinutes = otpProps.getRateLimit().getWindowMinutes();
                throw new BizException(
                    ErrorCode.OTP_REQUEST_TOO_FREQUENT,
                    String.format("验证失败次数过多，请在%d分钟后重试", windowMinutes)
                );
            }
        }
    }

    /**
     * 记录验证失败的尝试
     * @param email 邮箱地址
     */
    private void recordFailedAttempt(String email) {
        String attemptKey = OTP_ATTEMPT_KEY_PREFIX + email;
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

        log.warn("Failed OTP verification for email: {}, attempt: {}", email, attemptCount);
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
        String key = OTP_KEY_PREFIX + email;
        redisTemplate.delete(key);

        String attemptKey = OTP_ATTEMPT_KEY_PREFIX + email;
        redisTemplate.delete(attemptKey);

        log.info("OTP deleted for email: {}", email);
    }
}
