package nan.produced.prism.auth.security.console;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.domain.user.AdminUserEntity;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.AdminUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleAdminBootstrapRunner implements ApplicationRunner {

    private final ConsoleBootstrapProps props;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props == null || !props.isEnabled()) {
            return;
        }

        String username = props.getUsername();
        String password = props.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("Console bootstrap skipped: username/password is blank");
            return;
        }

        // Ensure we never create multiple global ADMIN accounts.
        if (adminUserRepository.findFirstByUserType(UserType.ADMIN).isPresent()) {
            return;
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);
        AdminUserEntity admin = new AdminUserEntity();
        admin.setPublicId(username.trim());
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setPasswordAlgo("bcrypt");
        admin.setUserType(UserType.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMetadata("{}");

        adminUserRepository.save(admin);
        log.warn("Console bootstrap created initial ADMIN account: username={}", admin.getPublicId());
    }
}

