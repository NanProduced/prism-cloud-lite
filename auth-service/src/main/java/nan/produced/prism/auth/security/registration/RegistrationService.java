package nan.produced.prism.auth.security.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import nan.produced.prism.auth.common.util.PublicIdGenerator;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.email.EmailService;
import nan.produced.prism.auth.security.password.PasswordPolicy;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 注册服务
 * 负责邮箱+验证码注册流程：申请OTP → 验证OTP → 设置密码
 * 采用 JIT Provisioning 模式：用户资料在首次登录后由 Core-Service 自动创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EmailOtpService emailOtpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final EndUserRepository endUserRepository;
    private final OtpProps otpProps;
    private final VerificationTokenService verificationTokenService;
    private final LoginAliasRepository loginAliasRepository;

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
        String otp = emailOtpService.generateAndStoreOtp(email);

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
        emailOtpService.verifyOtp(email, otp);

        // 生成临时令牌（用于下一步设置密码）
        String verificationToken = UUID.randomUUID().toString();

        // 将验证令牌存储到Redis，有效期为30分钟
        verificationTokenService.storeToken(email, verificationToken);

        log.info("OTP verified successfully for email: {}", email);
        return verificationToken;
    }

    /**
     * 完成注册 - 第三步
     * 创建用户账户（JIT Provisioning模式：用户资料将在首次登录后自动创建）
     * @param email 邮箱地址
     * @param password 密码（明文）
     * @param verificationToken 验证令牌（OTP验证后获得）
     * @throws BizException 如果邮箱已被注册、密码验证失败或验证令牌无效
     */
    @Transactional
    public void completeRegistration(String email, String password, String verificationToken) {
        // 验证令牌（确保用户已完成OTP验证）
        verificationTokenService.validateAndConsumeToken(email, verificationToken);

        // 再次检查邮箱是否已被注册（防竞态条件）
        if (endUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 验证密码强度（可能抛出BizException）
        PasswordPolicy.validateOrThrow(password);

        // 创建用户实体
        EndUserEntity user = new EndUserEntity();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPublicId(PublicIdGenerator.generate());  // 生成有意义的 publicId
        user.setMetadata("{}");

        // 保存到Auth-Service数据库
        user = endUserRepository.save(user);
        log.info("User registered successfully in Auth-Service: id={}, publicId={}, email={}",
            user.getId(), user.getPublicId(), email);

        LoginAliasEntity loginAlias = new LoginAliasEntity();
        loginAlias.setUser(user);
        loginAlias.setAliasType(LoginAliasType.EMAIL);
        loginAlias.setAliasValue(email);

         loginAliasRepository.save(loginAlias);

        // 注册完成 用户资料将在首次登录后通过 JIT Provisioning 自动创建
    }

}
