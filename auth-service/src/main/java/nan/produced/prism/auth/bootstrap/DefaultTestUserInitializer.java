package nan.produced.prism.auth.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTestUserInitializer implements ApplicationRunner {

    private static final String DEFAULT_EMAIL = "nanproduced@gmail.com";
    private static final String DEFAULT_DISPLAY_NAME = "NanTest";
    private static final String DEFAULT_PASSWORD = "Nan12091209";

    private final LoginAliasRepository loginAliasRepository;
    private final EndUserRepository endUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean exists = loginAliasRepository.findByValueAndType(DEFAULT_EMAIL, LoginAliasType.EMAIL).isPresent();
        if (exists) {
            log.debug("Default test user already exists: {}", DEFAULT_EMAIL);
            return;
        }

        EndUserEntity user = new EndUserEntity();
        user.setPublicId(UUID.randomUUID().toString());
        user.setEmail(DEFAULT_EMAIL);
        user.setDisplayName(DEFAULT_DISPLAY_NAME);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setPasswordAlgo("bcrypt");
        user.setUserType(UserType.END_USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMetadata("{}");

        EndUserEntity saved = endUserRepository.save(user);

        LoginAliasEntity alias = new LoginAliasEntity();
        alias.setAliasType(LoginAliasType.EMAIL);
        alias.setAliasValue(DEFAULT_EMAIL);
        alias.setUser(saved);
        loginAliasRepository.save(alias);

        log.info("Created default test user '{}' ({}) for local testing", DEFAULT_DISPLAY_NAME, DEFAULT_EMAIL);
    }
}