package nan.produced.prism.gateway.security.config;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.gateway.security.authentication.RefreshTokenErrorMapClientManager;
import nan.produced.prism.gateway.security.filter.RemoveJwtFilter;
import nan.produced.prism.gateway.security.handler.SaveRequestOAuth2AuthorizationRequestResolver;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                                                 OAuth2AuthorizationRequestResolver saveRequestOAuth2AuthorizationRequestResolver,
                                                 SecurityContextRepository securityContextRepository,
                                                 @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .securityMatcher(
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        "/logout",
                        "/logout/backchannel"
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                // 确保两个 FilterChain 使用相同的 SecurityContextRepository
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
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
                                                         RemoveJwtFilter removeJwtFilter,
                                                         SecurityContextRepository securityContextRepository,
                                                         @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                // 关键：从 Session 中恢复 SecurityContext（支持 OAuth2 Login 建立的会话）
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
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

    /**
     * 共享的 SecurityContextRepository，用于在多个 FilterChain 之间共享 Session 认证状态
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewaySecurityProps gatewaySecurityProps) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(gatewaySecurityProps.getCors().getAllowedOrigins());
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addAllowedMethod(CorsConfiguration.ALL);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
