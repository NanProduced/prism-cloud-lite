package nan.produced.prism.gateway.security.authentication;

import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 自定义 OAuth2AuthorizedClientManager
 * <p>
 *     OAuth2 Client refresh_token过期后触发登录（避免返回500）
 * </p>
 *
 * @author Nan
 */
public class RefreshTokenErrorMapClientManager implements OAuth2AuthorizedClientManager {

    private final DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager;

    public RefreshTokenErrorMapClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                             OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository) {

        this.defaultOAuth2AuthorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientRepository);

        OAuth2AuthorizedClientProvider auth2AuthorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .clientCredentials()
                .build();

        this.defaultOAuth2AuthorizedClientManager.setAuthorizedClientProvider(auth2AuthorizedClientProvider);
    }

    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest authorizeRequest) {
        try {
            // 委托给默认管理器执行 (包含自动刷新 Token 的逻辑)
            return this.defaultOAuth2AuthorizedClientManager.authorize(authorizeRequest);
        } catch (ClientAuthorizationException e) {
            // 如果 refresh_token 过期，Auth Server 通常返回 invalid_grant
            // Spring Security 会抛出 ClientAuthorizationException

            // 将其转换为 CredentialsExpiredException
            // 这样 ExceptionTranslationFilter 就会捕获它，并调用 AuthenticationEntryPoint (重定向到登录页)
            throw new CredentialsExpiredException("REFRESH_TOKEN expired", e);
        }
    }
}
