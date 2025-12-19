package nan.produced.prism.auth.internal.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.security.password.PasswordPolicy;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InternalAccountSecurityService {

    private final EndUserRepository endUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RememberMeTokenService rememberMeTokenService;

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
}

