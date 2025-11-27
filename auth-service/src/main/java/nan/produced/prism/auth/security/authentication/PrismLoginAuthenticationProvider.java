package nan.produced.prism.auth.security.authentication;

import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * 登录认证提供者
 *
 * @author Nan
 */
public class PrismLoginAuthenticationProvider implements AuthenticationProvider {

    private final PrismLoginInterface loginService;

    public PrismLoginAuthenticationProvider(PrismLoginInterface loginService) {
        this.loginService = loginService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PrismAuthenticationToken authenticationToken = (PrismAuthenticationToken) authentication;
        PrismUserPrincipal prismUserPrincipal = this.loginService.loadUserByAuthParams(authenticationToken.getAuthParams());
        this.loginService.authenticateUser(authenticationToken.getAuthParams(), prismUserPrincipal);
        PrismAuthenticationToken authenticatedToken = new PrismAuthenticationToken(prismUserPrincipal, null, prismUserPrincipal.getAuthorities(), authenticationToken.getAuthParams());
        authenticatedToken.setAuthParams(null);
        return authenticatedToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (PrismAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
