package nan.produced.prism.auth.oauth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.oauth.authorization.JdbcOidcAuthorizationService;
import nan.produced.prism.auth.oauth.authorization.OidcAuthorizationService;
import nan.produced.prism.auth.oauth.oidc.PrismOidcTokenCustomer;
import nan.produced.prism.auth.oauth.oidc.PrismOidcUserInfoMapper;
import nan.produced.prism.auth.oauth.slo.BackChannelLogoutHandler;
import nan.produced.prism.auth.oauth.slo.OidcLogoutErrorRedirectHandler;
import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.subscription.SubscriptionService;
import nan.produced.prism.auth.security.login.handler.SpaRedirectAuthenticationEntryPoint;
import nan.produced.prism.auth.utils.JwkUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final SecurityProps securityProps;

    private final PrismOidcUserInfoMapper oidcUserInfoMapper;

    /**
     * 授权服务器安全过滤链
     * <p>
     *     最高优先级，位于表单登录前
     * </p>
     *
     * @param http 请求
     * @return 过滤链
     * @throws Exception
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                     BackChannelLogoutHandler backChannelLogoutHandler,
                                                                     OidcLogoutErrorRedirectHandler oidcLogoutErrorRedirectHandler,
                                                                     SpaRedirectAuthenticationEntryPoint spaRedirectAuthenticationEntryPoint) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        // 配置 OIDC
        authorizationServerConfigurer
                .oidc(oidc -> oidc
                    // 配置用户信息返回
                    .userInfoEndpoint(userInfo -> userInfo
                            .userInfoMapper(oidcUserInfoMapper))
                    // 配置SLO处理(Back-Channel Logout)
                    .logoutEndpoint(logout -> logout
                            .logoutResponseHandler(backChannelLogoutHandler)
                            .errorResponseHandler(oidcLogoutErrorRedirectHandler)));

        return http
                // 配置授权服务器端点
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                // 配置授权服务器
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                // 配置资源服务器 (为了让 /userinfo 能解析 Access Token)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()))
                // 配置授权服务器权限
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/.well-known/**", "/oauth2/jwks").permitAll()
                        .anyRequest().authenticated())
                // 未登录访问 /oauth2/authorize 时，跳转到 SPA 登录页并携带 continue 参数
                .exceptionHandling(ex -> ex.authenticationEntryPoint(spaRedirectAuthenticationEntryPoint))
                .build();

    }


    /**
     * 配置注册客户端持久化仓库
     * @param jdbcTemplate 数据源
     * @param passwordEncoder 密码编码器
     * @return 注册客户端仓库
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        JdbcRegisteredClientRepository jdbcRegisteredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);

        String gatewayClientId = securityProps.getOauth2().getClient().getPrismGatewayClient().getClientId();
        RegisteredClient existing = jdbcRegisteredClientRepository.findByClientId(gatewayClientId);
        RegisteredClient desired = buildGatewayClient(passwordEncoder, existing);

        if (existing == null || needsUpdate(existing, desired)) {
            jdbcRegisteredClientRepository.save(desired);
        }
        return jdbcRegisteredClientRepository;
    }

    /**
     * 默认先创建 Prism Cloud 平台 gateway 客户端
     *
     * @param passwordEncoder 密码编码器
     * @return 注册客户端 - Prism Cloud 平台 gateway 默认客户端
     */
    private RegisteredClient buildGatewayClient(PasswordEncoder passwordEncoder, RegisteredClient existing) {
        String scopeRaw = securityProps.getOauth2().getClient().getPrismGatewayClient().getScope();
        Set<String> scopes = parseScopes(scopeRaw);

        String rawSecret = securityProps.getOauth2().getClient().getPrismGatewayClient().getClientSecret();
        String encodedSecret = existing != null && existing.getClientSecret() != null
                ? existing.getClientSecret()
                : passwordEncoder.encode(rawSecret);

        RegisteredClient.Builder builder = RegisteredClient.withId(existing != null ? existing.getId() : UUID.randomUUID().toString())
                .clientId(securityProps.getOauth2().getClient().getPrismGatewayClient().getClientId())
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(securityProps.getOauth2().getClient().getPrismGatewayClient().getRedirectUri())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .setting("settings.client.backchannel-logout-uri",
                                securityProps.getOauth2().getClient().getPrismGatewayClient().getBackchannelLogoutUri())
                        .setting("settings.client.backchannel-logout-session-required",
                                Boolean.TRUE)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(securityProps.getOauth2().getClient().getPrismGatewayClient().getAccessTokenValidityMinutes()))
                        .refreshTokenTimeToLive(Duration.ofDays(securityProps.getOauth2().getClient().getPrismGatewayClient().getRefreshTokenValidityMinutes()))
                        .build())
                ;

        securityProps.getOauth2().getClient().getPrismGatewayClient()
                .resolvePostLogoutRedirectUris()
                .forEach(builder::postLogoutRedirectUri);

        scopes.forEach(builder::scope);
        return builder.build();
    }

    private Set<String> parseScopes(String raw) {
        Set<String> scopes = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            Arrays.stream(raw.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(it -> !it.isBlank())
                    .forEach(scopes::add);
        }
        // OIDC requires `openid`; keep it always enabled for the default gateway client.
        scopes.add("openid");
        return scopes;
    }

    private boolean needsUpdate(RegisteredClient existing, RegisteredClient desired) {
        if (existing == null || desired == null) {
            return true;
        }
        if (!existing.getRedirectUris().equals(desired.getRedirectUris())) {
            return true;
        }
        if (!existing.getPostLogoutRedirectUris().equals(desired.getPostLogoutRedirectUris())) {
            return true;
        }
        return !existing.getScopes().equals(desired.getScopes());
    }

    /**
     * 配置授权服务
     *
     * @param jdbcTemplate 数据源
     * @param repository 注册客户端仓库
     * @return 授权服务(自定义增强实现)
     */
    @Bean
    public OidcAuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository repository) {
        JdbcOidcAuthorizationService authorizationService = new JdbcOidcAuthorizationService(jdbcTemplate, repository);

        // 创建专用的 ObjectMapper，不复用 Spring Boot 默认的 ObjectMapper
        // 避免 ImmutableCollections$ListN 等类型的序列化/反序列化问题
        ObjectMapper authorizationObjectMapper = new ObjectMapper();
        ClassLoader classLoader = AuthorizationServerConfig.class.getClassLoader();
        authorizationObjectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        authorizationObjectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        authorizationObjectMapper.addMixIn(PrismUserPrincipal.class, PrismUserPrincipalAllowlistMixin.class);


        JdbcOidcAuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOidcAuthorizationService.OAuth2AuthorizationRowMapper(repository);
        rowMapper.setObjectMapper(authorizationObjectMapper);
        authorizationService.setAuthorizationRowMapper(rowMapper);

        JdbcOidcAuthorizationService.OAuth2AuthorizationParametersMapper parametersMapper =
                new JdbcOidcAuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(authorizationObjectMapper);
        authorizationService.setAuthorizationParametersMapper(parametersMapper);

        return authorizationService;
    }

    /**
     * Jackson Mixin for {@link PrismUserPrincipal} serialization in OAuth2 authorization data.
     * <p>
     * - {@code @JsonTypeInfo}: Ensures class type information is included during serialization,
     *   allowing correct deserialization back to {@code PrismUserPrincipal}.
     * - {@code @JsonIgnoreProperties}: Prevents deserialization failures when stored JSON
     *   contains fields that no longer exist in the class.
     * </p>
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
    @JsonIgnoreProperties(ignoreUnknown = true)
    static abstract class PrismUserPrincipalAllowlistMixin {
    }

    /**
     * 配置授权同意服务
     *
     * @param jdbcTemplate 数据源
     * @param repository 注册客户端仓库
     * @return 授权同意服务
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate, RegisteredClientRepository repository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, repository);
    }

    /**
     * 配置 JWT 令牌定制器
     *
     * @return JWT令牌定制器
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(SubscriptionService subscriptionService) {
        return new PrismOidcTokenCustomer(subscriptionService);
    }

    /**
     * 配置 JWK 源
     * @return JWK源
     * @throws NoSuchAlgorithmException 找不到算法异常
     * @throws IOException 读取异常
     * @throws InvalidKeySpecException 密钥格式错误
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        RSAKey rsaKey = JwkUtils.convertRsaKey(securityProps);
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    /**
     * 配置 JWT 编码器
     * @param jwkSource JWK源
     * @return JWT编码器
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

}
