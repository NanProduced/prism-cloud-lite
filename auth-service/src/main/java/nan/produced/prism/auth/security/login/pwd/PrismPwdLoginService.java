package nan.produced.prism.auth.security.login.pwd;

import nan.produced.prism.auth.security.login.LoginAuthType;
import nan.produced.prism.auth.security.login.LoginAuthTypeConstants;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * 密码登录服务
 *
 * @author Nan
 */
public class PrismPwdLoginService implements PrismLoginInterface {

    private UserDetailsService userDetailsService;

    private PasswordEncoder passwordEncoder;

    public PrismPwdLoginService(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        String authType = authParams.get(LoginAuthTypeConstants.AUTH_TYPE);
        if (LoginAuthType.EMAIL_PWD.name().equals(authType)) {
            return (PrismUserPrincipal) userDetailsService.loadUserByUsername(authParams.get(LoginAuthTypeConstants.EMAIL));
        }
        else if (LoginAuthType.PHONE_PWD.name().equals(authType)) {
            return (PrismUserPrincipal) userDetailsService.loadUserByUsername(authParams.get(LoginAuthTypeConstants.PHONE));
        }
        throw new UsernameNotFoundException("auth_type is invalid");
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        boolean passwordMatches = passwordEncoder.matches(authParams.get(LoginAuthTypeConstants.PASSWORD), userPrincipal.getPassword());
        if (!passwordMatches) {
            throw new BadCredentialsException("password is invalid");
        }

    }
}
