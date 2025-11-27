package nan.produced.prism.auth.oauth.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.oauth.authorization.JdbcOidcAuthorizationService;
import nan.produced.prism.auth.oauth.authorization.OidcAuthorizationService;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final SecurityProps securityProps;


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

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey =
    }

}
