package nan.produced.prism.auth.oauth.slo;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.openid.connect.sdk.BackChannelLogoutRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.oauth.authorization.OidcAuthorizationService;
import nan.produced.prism.auth.oauth.oidc.OidcLoginState;
import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.utils.JwkUtils;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.CLAIM_LOGIN_STATE;

@Slf4j
@Component
public class BackChannelLogoutHandler implements AuthenticationSuccessHandler {

    private final RegisteredClientRepository registeredClientRepository;

    private final OidcAuthorizationService oidcAuthorizationService;

    private final SecurityProps securityProps;

    private final OAuth2AuthorizationServerProperties authorizationServerProperties;

    private final RSAKey rsaKey;

    private final JWSSigner jwsSigner;

    private static final String BACK_CHANNEL_REQUIRED = "settings.client.backchannel-logout-session-required";

    private static final String BACK_CHANNEL_LOGOUT_URI = "settings.client.backchannel-logout-uri";

    @SneakyThrows
    public BackChannelLogoutHandler(RegisteredClientRepository registeredClientRepository,
                                    OidcAuthorizationService oidcAuthorizationService,
                                    SecurityProps securityProps,
                                    OAuth2AuthorizationServerProperties authorizationServerProperties) {
        this.registeredClientRepository = registeredClientRepository;
        this.oidcAuthorizationService = oidcAuthorizationService;
        this.securityProps = securityProps;
        this.authorizationServerProperties = authorizationServerProperties;

        rsaKey = JwkUtils.convertRsaKey(securityProps);
        jwsSigner = new RSASSASigner(rsaKey);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OidcLogoutAuthenticationToken logoutAuthenticationToken = (OidcLogoutAuthenticationToken) authentication;
        String sessionId = logoutAuthenticationToken.getSessionId();
        String idTokenHint = logoutAuthenticationToken.getIdTokenHint();
        // 查询当前用户的授权信息
        OAuth2Authorization currentAuthorization = oidcAuthorizationService.findByIdToken(idTokenHint);
        // 查询当前调用SLO的客户端
        RegisteredClient callingClient = registeredClientRepository.findById(currentAuthorization.getRegisteredClientId());
        // 如果当前调用SLO的Client不存在
        if (callingClient == null) {
            log.warn("SLO - channel back logout - can't find current client info: {}", currentAuthorization.getRegisteredClientId());
            response.sendError(HttpStatus.BAD_REQUEST.value());
            return;
        }

        if (securityProps.getSlo().isEnabled()) {
            // OP本地session立即失效
            new SecurityContextLogoutHandler().logout(request, response, authentication);

            List<OAuth2Authorization> relatedAuth = oidcAuthorizationService.findBySessionId(sessionId);
            relatedAuth.forEach(auth -> processLogout(auth, currentAuthorization));
        }

        response.sendRedirect(determinePostLogoutRedirectUri(request, callingClient));



    }

    private void processLogout(OAuth2Authorization authorization, OAuth2Authorization currentAuthorization) {
        authorization = OAuth2Authorization.from(authorization)
                .attribute(CLAIM_LOGIN_STATE, OidcLoginState.LOGOUT.getCode())
                .build();

        if (authorization.getRefreshToken() != null) {
            invalidate(authorization, authorization.getRefreshToken().getToken());
        }
        else {
            log.warn("SLO - channel back logout - {} missing refresh_token", authorization.getId());
        }

        oidcAuthorizationService.save(authorization);

        if (!currentAuthorization.getRegisteredClientId().equals(authorization.getRegisteredClientId())) {
            RegisteredClient registeredClient = registeredClientRepository.findById(currentAuthorization.getRegisteredClientId());
            if (registeredClient == null) {
                log.warn("SLO - channel back logout - can't find client info: {}", currentAuthorization.getRegisteredClientId());
                return;
            }
            Boolean backChannelLogoutRequired = registeredClient.getClientSettings().getSetting(BACK_CHANNEL_REQUIRED);
            String backChannelLogoutUri = registeredClient.getClientSettings().getSetting(BACK_CHANNEL_LOGOUT_URI);
            if (Boolean.TRUE.equals(backChannelLogoutRequired) && StringUtils.hasText(backChannelLogoutUri)) {
                JWT logoutToken = generateLogoutToken(registeredClient, authorization);
                sendBackChannelLogoutRequest(backChannelLogoutUri, logoutToken);
            }
        }
    }

    @SneakyThrows
    private JWT generateLogoutToken(RegisteredClient registeredClient, OAuth2Authorization authorization) {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(authorizationServerProperties.getIssuer())
                .subject(authorization.getPrincipalName())
                .audience(registeredClient.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + registeredClient.getTokenSettings().getAccessTokenTimeToLive().toMillis()))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claimsSet
        );

        signedJWT.sign(jwsSigner);
        return signedJWT;
    }

    /**
     * 发送BackChannelLogoutRequest
     * @param backChannelLogoutUri  登出地址
     * @param logoutToken   登出Token
     */
    private void sendBackChannelLogoutRequest(String backChannelLogoutUri, JWT logoutToken) {
        try {
            URI backChannelLogoutEndpointForRP = new URI(backChannelLogoutUri);
            BackChannelLogoutRequest backChannelLogoutRequest = new BackChannelLogoutRequest(backChannelLogoutEndpointForRP, logoutToken);
            HTTPResponse httpResponse = backChannelLogoutRequest.toHTTPRequest().send();
            if (httpResponse.indicatesSuccess()) {
                log.info("SLO - channel back logout success - {}", httpResponse.getStatusCode());
            }
            else {
                log.warn("SLO - channel back logout failed - {}", httpResponse.getStatusCode());
            }
        } catch (URISyntaxException | IOException e) {
            log.error("SLO - channel back logout failed - {}", e.getMessage());
        }
    }

    /**
     * 确定登出后的跳转地址
     * @param request 请求
     * @param registeredClient 注册客户端
     * @return 登出后的跳转地址
     */
    private String determinePostLogoutRedirectUri(HttpServletRequest request, RegisteredClient registeredClient) {
        String postLogoutRedirectUri = request.getParameter("post_logout_redirect_uri");
        String state = request.getParameter(OAuth2ParameterNames.STATE);

        if (StringUtils.hasText(postLogoutRedirectUri) && registeredClient != null && registeredClient.getPostLogoutRedirectUris().contains(postLogoutRedirectUri)) {
            return StringUtils.hasText(state) ? String.format("%s?state=%s", postLogoutRedirectUri, state) : postLogoutRedirectUri;
        }
        else {
            log.warn("SLO - channel back logout - invalid post_logout_redirect_uri: {}", postLogoutRedirectUri);
            return securityProps.getSlo().getDefaultLogoutRedirectUri();
        }

    }

    /**
     *
     * @param authorization
     * @param token
     * @return
     * @param <T>
     * @see org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthenticationProviderUtils
     */
    private <T extends AbstractOAuth2Token> OAuth2Authorization invalidate(OAuth2Authorization authorization, T token) {
        // @formatter:off
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.from(authorization)
                .token(token,
                        (metadata) ->
                                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));

        if (OAuth2RefreshToken.class.isAssignableFrom(token.getClass())) {
            authorizationBuilder.token(
                    authorization.getAccessToken().getToken(),
                    (metadata) ->
                            metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));

            OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode =
                    authorization.getToken(OAuth2AuthorizationCode.class);
            if (authorizationCode != null && !authorizationCode.isInvalidated()) {
                authorizationBuilder.token(
                        authorizationCode.getToken(),
                        (metadata) ->
                                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
            }
        }
        // @formatter:on

        return authorizationBuilder.build();
    }


}
