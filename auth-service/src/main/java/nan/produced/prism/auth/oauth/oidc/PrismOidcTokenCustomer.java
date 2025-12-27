package nan.produced.prism.auth.oauth.oidc;

import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.subscription.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.*;

/**
 * 添加自定义Claim
 *
 * @author Nan
 */
public class PrismOidcTokenCustomer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final String CLIENT_SETTING_OWNER_USER_UUID = "prism.ownerUserUuid";
    private static final String CLIENT_SETTING_OWNER_PUBLIC_ID = "prism.ownerPublicId";

    private final SubscriptionService subscriptionService;

    public PrismOidcTokenCustomer(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        OAuth2TokenType tokenType = context.getTokenType();
        if (tokenType == null) {
            return;
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            extendAccessToken(context);
        }
        else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            extendRefreshToken(context);
        }
        else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            extendIdToken(context);
        }
    }

    private void extendAccessToken(JwtEncodingContext context) {
        Map<String, Object> claims = buildCommonClaims(context);

        // Special handling for API keys (client_credentials): make issued tokens look like end-user tokens
        // so that gateway/core-service can keep using CLOUD_AUTH consistently.
        String userUuid = null;
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            RegisteredClient client = context.getRegisteredClient();
            String ownerUserUuid = client == null ? null : asString(client.getClientSettings().getSetting(CLIENT_SETTING_OWNER_USER_UUID));
            String ownerPublicId = client == null ? null : asString(client.getClientSettings().getSetting(CLIENT_SETTING_OWNER_PUBLIC_ID));
            if (ownerUserUuid != null) {
                claims.put(CLAIM_USER_ID, ownerUserUuid);
                claims.put(CLAIM_ROLES, List.of("ROLE_END_USER"));
                userUuid = ownerUserUuid;
                if (ownerPublicId != null) {
                    context.getClaims().subject(ownerPublicId);
                }
            }
        }
        else {
            userUuid = resolveUserUuid(context.getPrincipal());
            if (userUuid != null) {
                claims.put(CLAIM_USER_ID, userUuid);
            }
        }

        claims.put(CLAIM_TIER, resolveTierOrDefault(userUuid));
        claims.forEach((key, value) -> context.getClaims().claim(key, value));
    }

    private void extendRefreshToken(JwtEncodingContext context) {

    }

    private void extendIdToken(JwtEncodingContext context) {
        Map<String, Object> claims = buildCommonClaims(context);
        String userUuid = resolveUserUuid(context.getPrincipal());
        if (userUuid != null) {
            claims.put(CLAIM_USER_ID, userUuid);
        }
        claims.put(CLAIM_TIER, resolveTierOrDefault(userUuid));
        // OIDC back-channel logout expects standard `sid` claim (session identifier).
        // Keep the existing custom claim name (`session_id`) for compatibility, but also provide `sid`.
        Object sessionId = claims.get(CLAIM_SESSION_ID);
        if (sessionId != null) {
            claims.put("sid", sessionId);
        }
        claims.forEach((key, value) -> context.getClaims().claim(key, value));
    }

    private Map<String, Object> buildCommonClaims(JwtEncodingContext context) {
        Map<String, Object> claims = new HashMap<>();
        Authentication authentication = context.getPrincipal();
        // Only treat PrismUserPrincipal authorities as roles (client_credentials principal authorities are not user roles).
        if (authentication != null && authentication.getPrincipal() instanceof PrismUserPrincipal) {
            List<String> authorities = authentication.getAuthorities() == null ? new ArrayList<>()
                    : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .sorted().toList();
            if (!authorities.isEmpty()) {
                claims.put(CLAIM_ROLES, authorities);
            }
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            claims.put(CLAIM_SESSION_ID, authorization.getAttribute(CLAIM_SESSION_ID));
        }
        claims.put(CLAIM_REALM, "END_USER");
        return claims;
    }

    private String resolveUserUuid(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof PrismUserPrincipal prismUserPrincipal && prismUserPrincipal.getId() != null) {
            return prismUserPrincipal.getId().toString();
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String resolveTierOrDefault(String userUuid) {
        if (subscriptionService == null || userUuid == null || userUuid.isBlank()) {
            return "FREE";
        }
        try {
            return subscriptionService.resolveTier(UUID.fromString(userUuid)).name();
        } catch (Exception ignore) {
            return "FREE";
        }
    }
}
