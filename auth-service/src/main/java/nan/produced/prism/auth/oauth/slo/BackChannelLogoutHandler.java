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
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.CLAIM_LOGIN_STATE;
import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.CLAIM_SESSION_ID;

@Slf4j
@Component
public class BackChannelLogoutHandler implements AuthenticationSuccessHandler {

    private final RegisteredClientRepository registeredClientRepository;

    private final OidcAuthorizationService oidcAuthorizationService;

    private final SecurityProps securityProps;

    private final RememberMeTokenService rememberMeTokenService;

    private final OAuth2AuthorizationServerProperties authorizationServerProperties;

    private final RSAKey rsaKey;

    private final JWSSigner jwsSigner;

    private static final String BACK_CHANNEL_REQUIRED = "settings.client.backchannel-logout-session-required";

    private static final String BACK_CHANNEL_LOGOUT_URI = "settings.client.backchannel-logout-uri";

    private static final String CLAIM_EVENTS = "events";

    private static final String EVENT_BACKCHANNEL_LOGOUT = "http://schemas.openid.net/event/backchannel-logout";

    @SneakyThrows
    public BackChannelLogoutHandler(RegisteredClientRepository registeredClientRepository,
                                    OidcAuthorizationService oidcAuthorizationService,
                                    SecurityProps securityProps,
                                    RememberMeTokenService rememberMeTokenService,
                                    OAuth2AuthorizationServerProperties authorizationServerProperties) {
        this.registeredClientRepository = registeredClientRepository;
        this.oidcAuthorizationService = oidcAuthorizationService;
        this.securityProps = securityProps;
        this.rememberMeTokenService = rememberMeTokenService;
        this.authorizationServerProperties = authorizationServerProperties;

        rsaKey = JwkUtils.convertRsaKey(securityProps);
        jwsSigner = new RSASSASigner(rsaKey);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OidcLogoutAuthenticationToken logoutAuthenticationToken)) {
            log.warn("SLO - unexpected logout authentication type: {}", authentication != null ? authentication.getClass() : null);
            response.sendError(HttpStatus.BAD_REQUEST.value());
            return;
        }

        String sessionId = logoutAuthenticationToken.getSessionId();
        String idTokenHint = logoutAuthenticationToken.getIdTokenHint();

        OAuth2Authorization currentAuthorization;
        RegisteredClient callingClient = null;
        if (StringUtils.hasText(idTokenHint)) {
            // 查询当前用户的授权信息
            currentAuthorization = oidcAuthorizationService.findByIdToken(idTokenHint);
            // 查询当前调用 SLO 的客户端
            if (currentAuthorization != null) {
                callingClient = registeredClientRepository.findById(currentAuthorization.getRegisteredClientId());
            }
        } else {
            currentAuthorization = null;
        }

        // 无法解析 calling client 时，不应直接 500（避免浏览器展示错误页）；改为 best-effort 登出并跳转到安全落点。
        if (callingClient == null) {
            log.warn("SLO - channel back logout - can't resolve calling client: sessionId={}, hasIdTokenHint={}",
                sessionId, StringUtils.hasText(idTokenHint));
            if (securityProps.getSlo().isEnabled()) {
                logoutLocallyWithoutInvalidatingSession(request, response, authentication);
            }
            response.sendRedirect(determinePostLogoutRedirectUriFallback(request));
            return;
        }

        if (securityProps.getSlo().isEnabled()) {
            // 注意：使用 Spring Session (Redis) 时，直接 invalidate HttpSession 可能在 commitSession 阶段触发
            // RedisSessionRepository.save -> IllegalStateException("Session was invalidated")，导致浏览器看到 500。
            // 这里采用“清空安全上下文但不 invalidate session”的方式完成 OP 侧登出。
            logoutLocallyWithoutInvalidatingSession(request, response, authentication);

            if (StringUtils.hasText(sessionId)) {
                List<OAuth2Authorization> relatedAuth = oidcAuthorizationService.findBySessionId(sessionId);
                relatedAuth.forEach(auth -> processLogout(auth, currentAuthorization));
            }
        }

        response.sendRedirect(determinePostLogoutRedirectUri(request, callingClient));



    }

    private void processLogout(OAuth2Authorization authorization, OAuth2Authorization currentAuthorization) {
        authorization = OAuth2Authorization.from(authorization)
                .attribute(CLAIM_LOGIN_STATE, OidcLoginState.LOGOUT.getCode())
                .build();

        if (authorization.getRefreshToken() != null) {
            authorization = invalidate(authorization, authorization.getRefreshToken().getToken());
        }
        else {
            if (authorization.getAccessToken() != null) {
                authorization = invalidate(authorization, authorization.getAccessToken().getToken());
            }
            log.debug("SLO - channel back logout - authorization {} missing refresh_token (refresh tokens may be disabled / not issued)", authorization.getId());
        }

        oidcAuthorizationService.save(authorization);

        if (!currentAuthorization.getRegisteredClientId().equals(authorization.getRegisteredClientId())) {
            RegisteredClient registeredClient = registeredClientRepository.findById(authorization.getRegisteredClientId());
            if (registeredClient == null) {
                log.warn("SLO - channel back logout - can't find client info: {}", authorization.getRegisteredClientId());
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

    private String determinePostLogoutRedirectUriFallback(HttpServletRequest request) {
        String requested = request != null ? request.getParameter("post_logout_redirect_uri") : null;
        String state = request != null ? request.getParameter(OAuth2ParameterNames.STATE) : null;

        List<String> allowlist = securityProps.getOauth2().getClient().getPrismGatewayClient().resolvePostLogoutRedirectUris();
        String target;
        if (StringUtils.hasText(requested) && allowlist.contains(requested)) {
            target = requested;
        }
        else if (allowlist != null && !allowlist.isEmpty() && StringUtils.hasText(allowlist.getFirst())) {
            target = allowlist.getFirst();
        }
        else {
            target = securityProps.getSlo().getDefaultLogoutRedirectUri();
        }

        return StringUtils.hasText(state) ? String.format("%s?state=%s", target, state) : target;
    }

    private void logoutLocallyWithoutInvalidatingSession(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        Authentication authentication) {
        // If remember-me is enabled, clear the cookie as well; otherwise the next /authorize may silently login again.
        try {
            rememberMeTokenService.clearRememberMeCookie(response);
        } catch (Exception ignore) {
            // ignored
        }
        SecurityContextLogoutHandler handler = new SecurityContextLogoutHandler();
        handler.setInvalidateHttpSession(false);
        handler.logout(request, response, authentication);
    }

    @SneakyThrows
    private JWT generateLogoutToken(RegisteredClient registeredClient, OAuth2Authorization authorization) {
        Object sid = authorization != null ? authorization.getAttribute(CLAIM_SESSION_ID) : null;
        Map<String, Object> events = new HashMap<>();
        events.put(EVENT_BACKCHANNEL_LOGOUT, Map.of());

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(authorizationServerProperties.getIssuer())
                .subject(authorization.getPrincipalName())
                .audience(registeredClient.getClientId())
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_EVENTS, events)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + registeredClient.getTokenSettings().getAccessTokenTimeToLive().toMillis()))
                ;

        // Standard OIDC session identifier claim for logout-token validation at RP side (Spring Security expects `sid`).
        if (sid != null) {
            builder.claim("sid", sid);
        }

        JWTClaimsSet claimsSet = builder.build();

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
            if (registeredClient != null && registeredClient.getPostLogoutRedirectUris() != null && !registeredClient.getPostLogoutRedirectUris().isEmpty()) {
                String fallback = registeredClient.getPostLogoutRedirectUris().iterator().next();
                return StringUtils.hasText(state) ? String.format("%s?state=%s", fallback, state) : fallback;
            }
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
