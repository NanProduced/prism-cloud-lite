package nan.produced.prism.auth.oauth.oidc;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.*;

@Component
@RequiredArgsConstructor
public class PrismOidcUserInfoMapper implements Function<OidcUserInfoAuthenticationContext, OidcUserInfo> {

    private final EndUserRepository endUserRepository;

    private static final Map<String, Set<String>> SCOPE_CLAIM_MAPPING;

    static {
        SCOPE_CLAIM_MAPPING = Map.of(
                OidcScopes.OPENID, Set.of(StandardClaimNames.SUB),
                OidcScopes.PROFILE, Set.of(StandardClaimNames.NAME),
                OidcScopes.EMAIL, Set.of(StandardClaimNames.EMAIL, StandardClaimNames.EMAIL_VERIFIED),
                OidcScopes.PHONE, Set.of(CLAIM_PHONE_NUMBER, CLAIM_PHONE_VERIFIED),
                SCOPE_PRISM_ACCOUNT, Set.of(CLAIM_ROLES),
                SCOPE_PRISM_SESSION, Set.of(CLAIM_SESSION_ID, CLAIM_LOGIN_STATE));
    }

    @Override
    public OidcUserInfo apply(OidcUserInfoAuthenticationContext authenticationContext) {
        OAuth2Authorization authorization = authenticationContext.getAuthorization();
        OidcIdToken idToken = Objects.requireNonNull(authorization.getToken(OidcIdToken.class)).getToken();
        OAuth2AccessToken accessToken = authenticationContext.getAccessToken();

        Map<String, Object> baseClaims = buildBaseClaims(authorization, idToken);
        Set<String> scopes = accessToken != null ? accessToken.getScopes() : Collections.emptySet();
        Map<String, Object> scopeRequestedClaims = filterClaimsByScope(baseClaims, scopes);
        return new OidcUserInfo(scopeRequestedClaims);
    }

    private Map<String, Object> buildBaseClaims(OAuth2Authorization authorization, OidcIdToken idToken) {
        Map<String, Object> claims = new HashMap<>();
        if (idToken != null && idToken.getClaims() != null) {
            claims.putAll(idToken.getClaims());
        }
        if (authorization != null) {
            addAttributeIfPresent(claims, CLAIM_SESSION_ID, authorization.getAttribute(CLAIM_SESSION_ID));
            addAttributeIfPresent(claims, CLAIM_LOGIN_STATE, authorization.getAttribute(CLAIM_LOGIN_STATE));
        }
        String subject = (String) claims.get(StandardClaimNames.SUB);
        if (!StringUtils.hasText(subject) && authorization != null) {
            subject = authorization.getPrincipalName();
        }
        if (StringUtils.hasText(subject)) {
            claims.put(StandardClaimNames.SUB, subject);
        }
        enrichWithUserProfile(claims, authorization);
        return claims;
    }

    private void enrichWithUserProfile(Map<String, Object> claims, OAuth2Authorization authorization) {
        if (authorization == null) {
            return;
        }
        endUserRepository.findByPublicId(authorization.getPrincipalName()).ifPresent(user -> {
            putIfHasText(claims, StandardClaimNames.EMAIL, user.getEmail());
            if (StringUtils.hasText(user.getEmail())) {
                claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE);
            }
            putIfHasText(claims, CLAIM_PHONE_NUMBER, user.getPhone());
            if (StringUtils.hasText(user.getPhone())) {
                claims.put(CLAIM_PHONE_VERIFIED, Boolean.TRUE);
            }
            if (StringUtils.hasText(user.getDisplayName())) {
                claims.put(StandardClaimNames.NAME, user.getDisplayName());
            }
        });
    }

    private Map<String, Object> filterClaimsByScope(Map<String, Object> claims, Set<String> scopes) {
        Map<String, Object> result = new HashMap<>();
        copyClaim(claims, result, StandardClaimNames.SUB);
        if (scopes == null || scopes.isEmpty()) {
            return result;
        }
        for (String scope : scopes) {
            Set<String> claimNames = SCOPE_CLAIM_MAPPING.get(scope);
            if (claimNames == null) {
                continue;
            }
            for (String claimName : claimNames) {
                copyClaim(claims, result, claimName);
            }
        }
        return result;
    }

    private void copyClaim(Map<String, Object> source, Map<String, Object> target, String claimName) {
        Object value = source.get(claimName);
        if (value != null) {
            target.put(claimName, value);
        }
    }

    private void putIfHasText(Map<String, Object> target, String claimName, String value) {
        if (StringUtils.hasText(value)) {
            target.put(claimName, value);
        }
    }

    private void addAttributeIfPresent(Map<String, Object> target, String name, Object value) {
        if (value != null) {
            target.put(name, value);
        }
    }
}
