package nan.produced.prism.auth.security.login;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.domain.user.repository.AdminUserRepository;
import nan.produced.prism.auth.security.login.otp.PrismEmailOtpLoginService;
import nan.produced.prism.auth.security.login.otp.PrismPhoneOtpLoginService;
import nan.produced.prism.auth.security.login.pwd.PrismAdminPwdLoginService;
import nan.produced.prism.auth.security.login.pwd.PrismPwdLoginService;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import nan.produced.prism.auth.security.otp.PnvService;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrismComboLoginService implements PrismLoginInterface{

    private final PasswordEncoder passwordEncoder;

    private final UserDetailsService userDetailsService;

    private final AdminUserRepository adminUserRepository;

    private final EmailOtpService emailOtpService;

    private final PnvService pnvService;

    private final Map<LoginAuthType, PrismLoginInterface> authTypeLoginServiceMap = new HashMap<>(4);

    @PostConstruct
    private void init() {
        PrismPwdLoginService prismPwdLoginService = new PrismPwdLoginService(userDetailsService, passwordEncoder);
        this.authTypeLoginServiceMap.put(LoginAuthType.EMAIL_PWD, prismPwdLoginService);
        this.authTypeLoginServiceMap.put(LoginAuthType.PHONE_PWD, prismPwdLoginService);
        this.authTypeLoginServiceMap.put(LoginAuthType.EMAIL_OTP, new PrismEmailOtpLoginService(userDetailsService, emailOtpService));
        this.authTypeLoginServiceMap.put(LoginAuthType.PHONE_OTP, new PrismPhoneOtpLoginService(userDetailsService, pnvService));
        this.authTypeLoginServiceMap.put(LoginAuthType.ADMIN_EMAIL_PWD, new PrismAdminPwdLoginService(adminUserRepository, passwordEncoder));
    }

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        LoginAuthType loginAuthType = LoginAuthType.fromString(authParams.get(LoginAuthTypeConstants.AUTH_TYPE));
        if (loginAuthType == null) {
            throw new UsernameNotFoundException("auth_type is invalid");
        }
        PrismLoginInterface loginService = authTypeLoginServiceMap.get(loginAuthType);
        return loginService.loadUserByAuthParams(authParams);
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        LoginAuthType loginAuthType = LoginAuthType.fromString(authParams.get(LoginAuthTypeConstants.AUTH_TYPE));
        if (loginAuthType == null) {
            throw new UsernameNotFoundException("auth_type is invalid");
        }
        PrismLoginInterface loginService = authTypeLoginServiceMap.get(loginAuthType);
        loginService.authenticateUser(authParams, userPrincipal);
    }
}
