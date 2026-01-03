package nan.produced.prism.auth.internal.service;

import java.security.SecureRandom;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.AdminUserEntity;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.AdminUserRepository;
import nan.produced.prism.auth.internal.dto.InternalAdminUserCreatedView;
import nan.produced.prism.auth.internal.dto.InternalAdminUserItem;
import nan.produced.prism.auth.internal.dto.InternalAdminUserPageView;
import nan.produced.prism.auth.internal.dto.InternalAdminUserPasswordResetView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InternalAdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public InternalAdminUserPageView list(UserType userType, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        UserType type = userType != null ? userType : UserType.MANAGER;

        Page<AdminUserEntity> result = adminUserRepository.findByUserType(type, PageRequest.of(safePage, safeSize));
        return InternalAdminUserPageView.builder()
            .items(result.getContent().stream().map(this::toItem).toList())
            .page(safePage)
            .size(safeSize)
            .total(result.getTotalElements())
            .build();
    }

    public InternalAdminUserItem getByPublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "publicId is required");
        }
        AdminUserEntity user = adminUserRepository.findByPublicId(normalizeUsername(publicId))
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        return toItem(user);
    }

    @Transactional
    public InternalAdminUserCreatedView createManager(String username, String password) {
        String normalized = normalizeUsername(username);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "username is required");
        }
        if (normalized.length() < 3 || normalized.length() > 36) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "username length must be 3~36");
        }
        if (adminUserRepository.findByEmail(normalized).isPresent() || adminUserRepository.findByPublicId(normalized).isPresent()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "username already exists");
        }

        String rawPassword = StringUtils.hasText(password) ? password.trim() : generatePassword(12);
        if (rawPassword.length() < 8 || rawPassword.length() > 64) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "password length must be 8~64");
        }

        AdminUserEntity entity = new AdminUserEntity();
        entity.setPublicId(normalized);
        entity.setEmail(normalized);
        entity.setUserType(UserType.MANAGER);
        entity.setStatus(UserStatus.ACTIVE);
        entity.setPasswordAlgo("bcrypt");
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        entity.setMetadata("{}");

        AdminUserEntity saved = adminUserRepository.save(entity);
        return new InternalAdminUserCreatedView(saved.getPublicId(), saved.getId().toString(), normalized, rawPassword);
    }

    @Transactional
    public void lock(String publicId) {
        String normalized = normalizeUsername(publicId);
        AdminUserEntity user = adminUserRepository.findByPublicId(normalized)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.LOCKED);
        adminUserRepository.save(user);
    }

    @Transactional
    public void unlock(String publicId) {
        String normalized = normalizeUsername(publicId);
        AdminUserEntity user = adminUserRepository.findByPublicId(normalized)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.ACTIVE);
        adminUserRepository.save(user);
    }

    @Transactional
    public InternalAdminUserPasswordResetView resetPassword(String publicId) {
        String normalized = normalizeUsername(publicId);
        AdminUserEntity user = adminUserRepository.findByPublicId(normalized)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        String newPassword = generatePassword(12);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordAlgo("bcrypt");
        adminUserRepository.save(user);

        return new InternalAdminUserPasswordResetView(user.getPublicId(), user.getId().toString(), newPassword);
    }

    private InternalAdminUserItem toItem(AdminUserEntity entity) {
        if (entity == null) {
            return null;
        }
        return InternalAdminUserItem.builder()
            .publicId(entity.getPublicId())
            .userId(entity.getId() != null ? entity.getId().toString() : null)
            .username(entity.getEmail())
            .userType(entity.getUserType() != null ? entity.getUserType().name() : null)
            .status(entity.getStatus() != null ? entity.getStatus().name() : null)
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String generatePassword(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
