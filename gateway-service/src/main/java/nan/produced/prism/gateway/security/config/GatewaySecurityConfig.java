package nan.produced.prism.gateway.security.config;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.gateway.security.handler.SaveRequestOAuth2AuthorizationRequestResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProps.class)
@RequiredArgsConstructor
public class GatewaySecurityConfig {

    private final AccessDeniedHandler accessDeniedHandler;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    @Order(0)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http,
                                                 GatewaySecurityProps gatewaySecurityProps,
                                                 OidcClientInitiatedLogoutSuccessHandler oidcClientInitiatedLogoutSuccessHandler,
                                                 OAuth2AuthorizationRequestResolver saveRequestOAuth2AuthorizationRequestResolver) throws Exception {
        return http
                .securityMatcher(
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        "/logout"
                )
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(saveRequestOAuth2AuthorizationRequestResolver))
                        .failureHandler((authenticationEntryPoint::commence)))
                .logout(logout -> logout
                        .logoutUrl(gatewaySecurityProps.getOauth2().getClient().getLogoutUri())
                        .logoutRequestMatcher(request ->
                                request.getRequestURI().equals(gatewaySecurityProps.getOauth2().getClient().getLogoutUri()) &&
                                        HttpMethod.GET.name().equals(request.getMethod()))
                        .logoutSuccessHandler(oidcClientInitiatedLogoutSuccessHandler))
                .oidcLogout(logout -> logout.backChannel(Customizer.withDefaults()))
                .oauth2Client(Customizer.withDefaults())
                .build();
    }


    @Bean
    @Primary
    public OAuth2AuthorizationRequestResolver saveRequestOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repository, GatewaySecurityProps gatewaySecurityProps) {
        return new SaveRequestOAuth2AuthorizationRequestResolver(repository, gatewaySecurityProps);
    }

    @Bean
    public OidcClientInitiatedLogoutSuccessHandler oidcClientInitiatedLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository, GatewaySecurityProps gatewaySecurityProps) {
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        // 设置 OIDC 登出后的回调地址
        handler.setPostLogoutRedirectUri(gatewaySecurityProps.getOauth2().getClient().getLogoutRedirectUri());
        // 如果当前 Session 已过期（没拿到 ID Token），或者 IdP 不支持 OIDC 登出，则跳转到默认地址
        handler.setDefaultTargetUrl(gatewaySecurityProps.getOauth2().getClient().getLogoutRedirectUri());
        return handler;
    }
}
