package nan.produced.prism.gateway.realtime.sse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import static nan.produced.prism.gateway.security.AuthClaimsConstant.CLAIM_USER_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayUserIdentityService {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    private final JwtDecoder jwtDecoder;

    public UUID resolveUserId(Authentication authentication, HttpServletRequest request) {
        Map<String, Object> claims = extractClaims(authentication, request);
        Object rawUserId = claims.get(CLAIM_USER_ID);
        if (rawUserId == null) {
            return null;
        }
        try {
            return UUID.fromString(rawUserId.toString());
        } catch (Exception e) {
            log.warn("SSE - invalid user_uuid claim: {}", rawUserId, e);
            return null;
        }
    }

    private Map<String, Object> extractClaims(Authentication authentication, HttpServletRequest request) {

        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new HashMap<>(jwt.getToken().getClaims());
        }

        if (authentication instanceof OAuth2AuthenticationToken oidc
                && oidc.getPrincipal() instanceof DefaultOidcUser oidcUser) {
            Map<String, Object> result = new HashMap<>(oidcUser.getClaims());
            Map<String, Object> accessTokenClaims = resolveAccessTokenClaims(oidc, request);
            if (!accessTokenClaims.isEmpty()) {
                result.putAll(accessTokenClaims);
            }
            return result;
        }

        return Collections.emptyMap();
    }

    private Map<String, Object> resolveAccessTokenClaims(OAuth2AuthenticationToken authenticationToken,
                                                        HttpServletRequest request) {
        if (authorizedClientRepository == null) {
            return Collections.emptyMap();
        }
        try {
            OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
                    authenticationToken.getAuthorizedClientRegistrationId(), authenticationToken, request);
            if (client == null || client.getAccessToken() == null) {
                return Collections.emptyMap();
            }
            Jwt jwt = jwtDecoder.decode(client.getAccessToken().getTokenValue());
            return new HashMap<>(jwt.getClaims());
        } catch (JwtException ex) {
            log.warn("SSE - failed to decode access token", ex);
            return Collections.emptyMap();
        } catch (Exception ex) {
            log.warn("SSE - failed to resolve access token claims", ex);
            return Collections.emptyMap();
        }
    }
}

