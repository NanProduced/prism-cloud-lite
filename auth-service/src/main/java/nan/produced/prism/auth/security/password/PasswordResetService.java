package nan.produced.prism.auth.security.password;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.audit.SecurityAuditService;
import nan.produced.prism.auth.security.email.EmailService;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.OtpScene;
import nan.produced.prism.auth.security.otp.PnvScene;
import nan.produced.prism.auth.security.otp.PnvService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmailOtpService emailOtpService;
    private final EmailService emailService;
    private final OtpProps otpProps;
    private final PnvService pnvService;
    private final LoginAliasRepository loginAliasRepository;
    private final EndUserRepository endUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RememberMeTokenService rememberMeTokenService;
    private final SecurityAuditService securityAuditService;

    @Transactional
    public void requestResetByEmail(String email) {
        String normalized = normalizeEmail(email);
        ensureUserExists(LoginAliasType.EMAIL, normalized);

        String otp = emailOtpService.generateAndStoreOtp(normalized, OtpScene.PASSWORD_RESET);
        emailService.sendOtpEmail(normalized, otp, otpProps.getValidityMinutes());
    }

    @Transactional
    public void requestResetByPhone(String phone) {
        String normalized = normalizePhone(phone);
        ensureUserExists(LoginAliasType.PHONE, normalized);

        if (Boolean.FALSE.equals(pnvService.canApplyPnv(normalized, PnvScene.PASSWORD_RESET))) {
            throw new BizException(ErrorCode.OTP_REQUEST_TOO_FREQUENT);
        }
        pnvService.sendPnvCode(normalized, PnvScene.PASSWORD_RESET);
    }

    @Transactional
    public void confirmResetByEmail(String email, String otp, String newPassword, HttpServletRequest request) {
        String normalized = normalizeEmail(email);
        PasswordPolicy.validateOrThrow(newPassword);
        emailOtpService.verifyOtp(normalized, otp, OtpScene.PASSWORD_RESET);

        EndUserEntity user = endUserRepository.findByEmailIgnoreCase(normalized)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        endUserRepository.save(user);
        rememberMeTokenService.revokeAll(user.getId());
        securityAuditService.recordPasswordChanged(user.getId(), request);
    }

    @Transactional
    public void confirmResetByPhone(String phone, String code, String newPassword, HttpServletRequest request) {
        String normalized = normalizePhone(phone);
        PasswordPolicy.validateOrThrow(newPassword);
        pnvService.verifyPnvCode(normalized, code, PnvScene.PASSWORD_RESET);

        EndUserEntity user = loginAliasRepository.findByValueAndType(normalized, LoginAliasType.PHONE)
            .map(LoginAliasEntity::getUser)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        endUserRepository.save(user);
        rememberMeTokenService.revokeAll(user.getId());
        securityAuditService.recordPasswordChanged(user.getId(), request);
    }

    private void ensureUserExists(LoginAliasType type, String value) {
        if (loginAliasRepository.findByValueAndType(value, type).isEmpty()) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
    }

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
}
