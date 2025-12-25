package nan.produced.prism.auth.security.login.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.email.EmailService;
import nan.produced.prism.auth.security.otp.OtpScene;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import nan.produced.prism.auth.security.otp.PnvScene;
import nan.produced.prism.auth.security.otp.PnvService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonLoginOtpService {

    private final EmailOtpService emailOtpService;
    private final EmailService emailService;
    private final OtpProps otpProps;
    private final PnvService pnvService;
    private final LoginAliasRepository loginAliasRepository;

    /**
     * 请求邮箱 OTP
     * @param email 邮箱
     */
    public void requestEmailOtp(String email) {
        String normalized = normalizeEmail(email);
        if (loginAliasRepository.findByValueAndType(normalized, LoginAliasType.EMAIL).isEmpty()) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        String otp = emailOtpService.generateAndStoreOtp(normalized, OtpScene.LOGIN);
        emailService.sendOtpEmail(email, otp, otpProps.getValidityMinutes());;
    }

    /**
     * 请求手机号登录验证码（PNV）
     * @param phone 手机号码
     */
    public void requestPhoneOtp(String phone) {
        String normalized = normalizePhone(phone);
        if (loginAliasRepository.findByValueAndType(normalized, LoginAliasType.PHONE).isEmpty()) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (Boolean.FALSE.equals(pnvService.canApplyPnv(normalized, PnvScene.LOGIN))) {
            throw new BizException(ErrorCode.OTP_REQUEST_TOO_FREQUENT);
        }
        pnvService.sendPnvCode(normalized, PnvScene.LOGIN);
    }

    /**
     * 兼容旧方法名（手机号验证码登录）。
     *
     * @param phone 手机号码
     */
    public void requestPnvCode(String phone) {
        requestPhoneOtp(phone);
    }

    /**
     * 邮箱格式化
     * @param email 邮箱
     * @return 格式化后的邮箱
     */
    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "phone is required");
        }
        String normalized = phone.trim();
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "phone is invalid");
        }
        return normalized;
    }

    /**
     * 掩码邮箱
     * @param email 邮箱
     * @return 掩码后的邮箱
     */
    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
