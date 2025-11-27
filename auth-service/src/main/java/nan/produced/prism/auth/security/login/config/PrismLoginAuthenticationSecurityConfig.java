package nan.produced.prism.auth.security.login.config;

import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.security.authentication.PrismLoginAuthenticationProgressingFilter;
import nan.produced.prism.auth.security.authentication.PrismLoginAuthenticationProvider;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.login.handler.PrismLoginRespJsonFailureHandler;
import nan.produced.prism.auth.security.login.handler.PrismLoginRespJsonSuccessHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * 拓展登录认证配置
 *
 * @author Nan
 */
public class PrismLoginAuthenticationSecurityConfig extends AbstractHttpConfigurer<PrismLoginAuthenticationSecurityConfig, HttpSecurity> {

    private final PrismLoginInterface loginService;

    private final SecurityProps securityProps;

    private final RequestCache requestCache;

    public PrismLoginAuthenticationSecurityConfig(PrismLoginInterface loginService, SecurityProps securityProps, RequestCache requestCache) {
        this.loginService = loginService;
        this.securityProps = securityProps;
        this.requestCache = requestCache;
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {

        PrismLoginAuthenticationProgressingFilter filter = new PrismLoginAuthenticationProgressingFilter(
                securityProps.getLogin().getLoginProcessingUrl(),
                http.getSharedObject(AuthenticationManager.class)
        );

        filter.setAuthenticationSuccessHandler(new PrismLoginRespJsonSuccessHandler(requestCache));
        filter.setAuthenticationFailureHandler(new PrismLoginRespJsonFailureHandler());

        PrismLoginAuthenticationProvider provider = new PrismLoginAuthenticationProvider(loginService);

        http.authenticationProvider(provider)
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    }
}
