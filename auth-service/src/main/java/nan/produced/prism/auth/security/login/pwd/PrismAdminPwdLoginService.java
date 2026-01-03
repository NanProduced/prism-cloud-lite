package nan.produced.prism.auth.security.login.pwd;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.domain.user.AdminUserEntity;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.AdminUserRepository;
import nan.produced.prism.auth.security.login.LoginAuthType;
import nan.produced.prism.auth.security.login.LoginAuthTypeConstants;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * Admin/Manager password login service for the management console.
 *
 * <p>Important: This login flow is intentionally isolated from the end-user login alias system.</p>
 */
@RequiredArgsConstructor
public class PrismAdminPwdLoginService implements PrismLoginInterface {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        String authType = authParams == null ? null : authParams.get(LoginAuthTypeConstants.AUTH_TYPE);
        if (!LoginAuthType.ADMIN_EMAIL_PWD.name().equalsIgnoreCase(authType)) {
            throw new UsernameNotFoundException("auth_type is invalid");
        }

        String rawEmail = authParams.get(LoginAuthTypeConstants.EMAIL);
        if (!StringUtils.hasText(rawEmail)) {
            throw new UsernameNotFoundException("email is required");
        }

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        AdminUserEntity entity = adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found for email " + email));

        UserType userType = entity.getUserType();
        if (userType != UserType.ADMIN && userType != UserType.MANAGER) {
            throw new UsernameNotFoundException("User is not a console admin");
        }

        return PrismUserPrincipal.fromAdmin(entity, defaultRoles(userType));
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        String password = authParams == null ? null : authParams.get(LoginAuthTypeConstants.PASSWORD);
        if (!StringUtils.hasText(password)) {
            throw new BadCredentialsException("password is required");
        }
        if (userPrincipal == null || !passwordEncoder.matches(password, userPrincipal.getPassword())) {
            throw new BadCredentialsException("password is invalid");
        }
    }

    private static List<String> defaultRoles(UserType userType) {
        return userType == UserType.MANAGER ? List.of("ROLE_MANAGER") : List.of("ROLE_ADMIN");
    }
}

