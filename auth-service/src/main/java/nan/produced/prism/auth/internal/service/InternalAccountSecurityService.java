package nan.produced.prism.auth.internal.service;

import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.otp.PnvScene;
import nan.produced.prism.auth.security.otp.PnvService;
import nan.produced.prism.auth.security.password.PasswordPolicy;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InternalAccountSecurityService {

    private final EndUserRepository endUserRepository;
    private final LoginAliasRepository loginAliasRepository;
    private final PasswordEncoder passwordEncoder;
    private final RememberMeTokenService rememberMeTokenService;
    private final PnvService pnvService;

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        if (userId == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "userId is required");
        }
        if (!StringUtils.hasText(currentPassword)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "currentPassword is required");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "newPassword is required");
        }

        EndUserEntity user = endUserRepository.findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "current password is invalid");
        }

        PasswordPolicy.validateOrThrow(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        endUserRepository.save(user);

        // 密码变更后，注销所有 remember-me 设备，要求重新登录。
        rememberMeTokenService.revokeAll(userId);
    }

    @Transactional
    public void requestBindPhoneOtp(UUID userId, String phone) {
        EndUserEntity user = requireUser(userId);
        String normalized = normalizePhone(phone);

        ensurePhoneNotUsedByOtherUser(user.getId(), normalized);

        if (Boolean.FALSE.equals(pnvService.canApplyPnv(normalized, PnvScene.BIND))) {
            throw new BizException(ErrorCode.OTP_REQUEST_TOO_FREQUENT);
        }
        pnvService.sendPnvCode(normalized, PnvScene.BIND);
    }

    @Transactional
    public void confirmBindPhone(UUID userId, String phone, String code) {
        EndUserEntity user = requireUser(userId);
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "code is required");
        }

        ensurePhoneNotUsedByOtherUser(user.getId(), normalized);
        pnvService.verifyPnvCode(normalized, code, PnvScene.BIND);

        user.setPhone(normalized);
        try {
            endUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.PHONE_ALREADY_IN_USE, "phone is already in use", ex);
        }

        // Ensure login alias exists for PHONE.
        if (loginAliasRepository.findByValueAndType(normalized, LoginAliasType.PHONE).isEmpty()) {
            LoginAliasEntity alias = new LoginAliasEntity();
            alias.setUser(user);
            alias.setAliasType(LoginAliasType.PHONE);
            alias.setAliasValue(normalized);
            try {
                loginAliasRepository.save(alias);
            } catch (DataIntegrityViolationException ex) {
                throw new BizException(ErrorCode.PHONE_ALREADY_IN_USE, "phone is already in use", ex);
            }
        }
    }

    private EndUserEntity requireUser(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "userId is required");
        }
        return endUserRepository.findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    private void ensurePhoneNotUsedByOtherUser(UUID userId, String phone) {
        loginAliasRepository.findByValueAndType(phone, LoginAliasType.PHONE)
            .ifPresent(alias -> {
                EndUserEntity owner = alias.getUser();
                if (owner != null && owner.getId() != null && !owner.getId().equals(userId)) {
                    throw new BizException(ErrorCode.PHONE_ALREADY_IN_USE);
                }
            });
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "phone is required");
        }
        String normalized = phone.trim();
        if (!normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "phone is invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
