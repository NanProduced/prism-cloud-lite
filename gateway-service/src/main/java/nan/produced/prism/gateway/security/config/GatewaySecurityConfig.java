package nan.produced.prism.gateway.security.config;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.gateway.security.authentication.RefreshTokenErrorMapClientManager;
import nan.produced.prism.gateway.security.filter.RemoveJwtFilter;
import nan.produced.prism.gateway.security.handler.SaveRequestOAuth2AuthorizationRequestResolver;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

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
    @Order(1)
    public SecurityFilterChain backendServiceFilterChain(HttpSecurity http,
                                                         GatewaySecurityProps gatewaySecurityProps,
                                                         AuthorizationManager<RequestAuthorizationContext> authorizationManager,
                                                         RemoveJwtFilter removeJwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(gatewaySecurityProps.getWhiteList().getUrls().toArray(String[]::new)).permitAll()
                        .anyRequest().access(authorizationManager))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(Customizer.withDefaults()))
                .addFilterAfter(removeJwtFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Primary
    public OAuth2AuthorizationRequestResolver saveRequestOAuth2AuthorizationRequestResolver(ClientRegistrationRepository repository) {
        return new SaveRequestOAuth2AuthorizationRequestResolver(repository);
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

    @Bean
    @Primary
    public OAuth2AuthorizedClientManager auth2AuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                      OAuth2AuthorizedClientRepository authorizedClientRepository) {
        return new RefreshTokenErrorMapClientManager(clientRegistrationRepository, authorizedClientRepository);
    }


}
