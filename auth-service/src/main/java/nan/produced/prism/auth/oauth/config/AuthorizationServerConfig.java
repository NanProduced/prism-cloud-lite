package nan.produced.prism.auth.oauth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.oauth.authorization.JdbcOidcAuthorizationService;
import nan.produced.prism.auth.oauth.authorization.OidcAuthorizationService;
import nan.produced.prism.auth.oauth.oidc.PrismOidcTokenCustomer;
import nan.produced.prism.auth.oauth.oidc.PrismOidcUserInfoMapper;
import nan.produced.prism.auth.oauth.slo.BackChannelLogoutHandler;
import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.utils.JwkUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final SecurityProps securityProps;

    private final PrismOidcUserInfoMapper oidcUserInfoMapper;

    private final BackChannelLogoutHandler backChannelLogoutHandler;

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
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        // 配置 OIDC
        authorizationServerConfigurer
                .oidc(oidc -> oidc
                    // 配置用户信息返回
                    .userInfoEndpoint(userInfo -> userInfo
                            .userInfoMapper(oidcUserInfoMapper))
                    // 配置SLO处理(Back-Channel Logout)
                    .logoutEndpoint(logout -> logout
                            .logoutResponseHandler(backChannelLogoutHandler)));

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
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login"))
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
        if (null == jdbcRegisteredClientRepository.findByClientId("gateway-service-client")) {
            jdbcRegisteredClientRepository.save(createDefaultGatewayClient(passwordEncoder));
        }
        return jdbcRegisteredClientRepository;
    }

    /**
     * 默认先创建 Prism Cloud 平台 gateway 客户端
     *
     * @param passwordEncoder 密码编码器
     * @return 注册客户端 - Prism Cloud 平台 gateway 默认客户端
     */
    private RegisteredClient createDefaultGatewayClient(PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(securityProps.getOauth2().getClient().getPrismGatewayClient().getClientId())
                .clientSecret(passwordEncoder.encode(securityProps.getOauth2().getClient().getPrismGatewayClient().getClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(securityProps.getOauth2().getClient().getPrismGatewayClient().getRedirectUri())
                .postLogoutRedirectUri(securityProps.getOauth2().getClient().getPrismGatewayClient().getLogoutRedirectUri())
                .scope(securityProps.getOauth2().getClient().getPrismGatewayClient().getScope())
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
                .build();
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
        return new JdbcOidcAuthorizationService(jdbcTemplate, repository);
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
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return new PrismOidcTokenCustomer();
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
