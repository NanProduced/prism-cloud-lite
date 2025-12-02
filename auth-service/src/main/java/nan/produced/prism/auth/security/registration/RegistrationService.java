package nan.produced.prism.auth.security.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import nan.produced.prism.auth.common.util.PublicIdGenerator;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.integration.CoreServiceClient;
import nan.produced.prism.auth.security.email.EmailService;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.OtpService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 注册服务
 * 负责邮箱+验证码注册流程：申请OTP → 验证OTP → 设置密码 → 初始化Core-Service用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final EndUserRepository endUserRepository;
    private final CoreServiceClient coreServiceClient;
    private final OtpProps otpProps;
    private final VerificationTokenService verificationTokenService;

    /**
     * 申请OTP - 第一步
     * @param email 邮箱地址
     * @throws BizException 如果邮箱已被注册
     * @throws InfraException 如果邮件发送或OTP生成失败
     */
    @Transactional
    public void requestOtp(String email) {
        // 检查邮箱是否已被注册
        if (endUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 生成OTP并存储到Redis
        String otp = otpService.generateAndStoreOtp(email);

        // 发送OTP邮件（可能抛出InfraException）
        long validityMinutes = otpProps.getValidityMinutes();
        emailService.sendOtpEmail(email, otp, validityMinutes);

        log.info("OTP requested successfully for email: {}", email);
    }

    /**
     * 验证OTP - 第二步
     * @param email 邮箱地址
     * @param otp OTP验证码
     * @return 验证令牌（后续用于设置密码）
     * @throws BizException 如果OTP验证失败
     */
    public String verifyOtp(String email, String otp) {
        // 验证OTP（可能抛出BizException）
        otpService.verifyOtp(email, otp);

        // 生成临时令牌（用于下一步设置密码）
        String verificationToken = UUID.randomUUID().toString();

        // 将验证令牌存储到Redis，有效期为30分钟
        verificationTokenService.storeToken(email, verificationToken);

        log.info("OTP verified successfully for email: {}", email);
        return verificationToken;
    }

    /**
     * 完成注册 - 第三步
     * 创建用户账户并初始化Core-Service用户资料和配额
     * @param email 邮箱地址
     * @param password 密码（明文）
     * @param displayName 显示名称（可选）
     * @param verificationToken 验证令牌（OTP验证后获得）
     * @throws BizException 如果邮箱已被注册、密码验证失败或验证令牌无效
     * @throws InfraException 如果Core-Service初始化失败
     */
    @Transactional
    public void completeRegistration(String email, String password, String displayName, String verificationToken) {
        // 验证令牌（确保用户已完成OTP验证）
        verificationTokenService.validateAndConsumeToken(email, verificationToken);

        // 再次检查邮箱是否已被注册（防竞态条件）
        if (endUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 验证密码强度（可能抛出BizException）
        validatePassword(password);

        // 创建用户实体
        EndUserEntity user = new EndUserEntity();
        user.setEmail(email.toLowerCase());
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPublicId(PublicIdGenerator.generate());  // 生成有意义的 publicId
        user.setMetadata("{}");

        // 保存到Auth-Service数据库
        user = endUserRepository.save(user);
        log.info("User created in Auth-Service with id: {}, email: {}", user.getId(), email);

        // 初始化Core-Service用户资料和配额
        try {
            CoreServiceClient.InitializeUserRequest request = new CoreServiceClient.InitializeUserRequest(
                user.getId().toString(),
                user.getPublicId(),
                user.getEmail(),
                user.getPhone(),
                user.getDisplayName(),
                "FREE"  // 默认为FREE等级
            );

            var apiResponse = coreServiceClient.initializeUser(request);

            // 规范要求：优先检查 ApiResponse.code，然后检查 data
            if (!"CORE-0000".equals(apiResponse.getCode())) {
                // Core-Service 返回错误码，回滚 Auth-Service 用户
                endUserRepository.delete(user);
                log.error("Core-Service returned error: code={}, message={}",
                    apiResponse.getCode(), apiResponse.getMessage());
                throw new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "用户初始化失败: " + apiResponse.getMessage()
                );
            }

            // 防御性检查：验证 data 内容
            if (apiResponse.getData() == null || !apiResponse.getData().success()) {
                endUserRepository.delete(user);
                log.error("Core-Service data validation failed: data={}", apiResponse.getData());
                throw new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "用户初始化失败: 响应数据异常"
                );
            }

            log.info("User initialized in Core-Service with coreUserId: {}",
                apiResponse.getData().coreUserId());
        } catch (InfraException e) {
            // 回滚：删除已创建的Auth-Service用户
            endUserRepository.delete(user);
            log.error("Failed to initialize user in Core-Service, rolled back Auth-Service user", e);
            throw e;
        } catch (Exception e) {
            // 回滚：删除已创建的Auth-Service用户
            endUserRepository.delete(user);
            log.error("Failed to initialize user in Core-Service, rolled back Auth-Service user", e);
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "用户初始化失败，注册已回滚，请重试", e);
        }

        log.info("Registration completed successfully for email: {}", email);
    }

    /**
     * 验证密码强度
     * @param password 密码
     * @throws BizException 如果密码不符合强度要求
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码长度至少为8位");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个大写字母");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个小写字母");
        }

        if (!password.matches(".*\\d.*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个数字");
        }
    }
}
