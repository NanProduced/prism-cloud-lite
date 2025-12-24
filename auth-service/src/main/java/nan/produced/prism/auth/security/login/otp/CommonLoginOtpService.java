package nan.produced.prism.auth.security.login.otp;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.email.EmailService;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonLoginOtpService {

    private final EmailOtpService emailOtpService;
    private final EmailService emailService;
    private final AsyncClient aliyunClient;
    private final OtpProps otpProps;
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

        String otp = emailOtpService.generateAndStoreOtp(normalized);
        emailService.sendOtpEmail(email, otp, otpProps.getValidityMinutes());;
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
