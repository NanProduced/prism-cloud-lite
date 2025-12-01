package nan.produced.prism.auth.oauth.oidc;

import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.*;

/**
 * 添加自定义Claim
 *
 * @author Nan
 */
public class PrismOidcTokenCustomer implements OAuth2TokenCustomizer<JwtEncodingContext> {

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
        String userUuid = resolveUserUuid(context.getPrincipal());
        if (userUuid != null) {
            claims.put(CLAIM_USER_ID, userUuid);
        }
        claims.forEach((key, value) -> context.getClaims().claim(key, value));
    }

    private void extendRefreshToken(JwtEncodingContext context) {

    }

    private void extendIdToken(JwtEncodingContext context) {
        Map<String, Object> claims = buildCommonClaims(context);
        String displayName = resolveDisplayName(context.getPrincipal());
        if (displayName != null) {
            claims.put(StandardClaimNames.NAME, displayName);
        }
        claims.forEach((key, value) -> context.getClaims().claim(key, value));
    }

    private Map<String, Object> buildCommonClaims(JwtEncodingContext context) {
        Map<String, Object> claims = new HashMap<>();
        Authentication authentication = context.getPrincipal();
        if (authentication != null) {
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
        claims.put(CLAIM_TIER, "FREE");
        claims.put(CLAIM_REALM, "END_USER");
        return claims;
    }

    private String resolveDisplayName(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof PrismUserPrincipal prismUserPrincipal) {
            return prismUserPrincipal.getDisplayName();
        }
        return null;
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
}