package nan.produced.prism.gateway.security.filter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.gateway.utils.JsonUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static nan.produced.prism.gateway.security.AuthClaimsConstant.CLAIM_ROLES;
import static nan.produced.prism.gateway.security.AuthClaimsConstant.CLAIM_TIER;
import static nan.produced.prism.gateway.security.AuthClaimsConstant.CLAIM_USER_ID;

@Component
@Slf4j
@Order(1)
@RequiredArgsConstructor
public class AddAuthHeaderFilter extends OncePerRequestFilter {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> claims = extractClaims(authentication, request);

        if (claims != null && !claims.isEmpty()) {
            try {
                String headerValue = buildUserHeader(claims);
                HttpServletRequest modifiedRequest = new CloudAuthRequestWrapper(request, headerValue);
                filterChain.doFilter(modifiedRequest, response);
                return;
            } catch (Exception e) {
                log.error("AddAuthHeader - Failed to build CLOUD_AUTH header", e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Map<String, Object> extractClaims(Authentication authentication, HttpServletRequest request) {

        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new HashMap<>(jwt.getToken().getClaims());
        }

        if (authentication instanceof OAuth2AuthenticationToken oidc && oidc.getPrincipal() instanceof DefaultOidcUser oidcUser) {
            Map<String, Object> result = new HashMap<>(oidcUser.getClaims());
            Map<String, Object> accessTokenClaims = resolveAccessTokenClaims(oidc, request);
            if (!accessTokenClaims.isEmpty()) {
                result.putAll(accessTokenClaims);
            }
            return result;
        }

        return Collections.emptyMap();
    }

    private Map<String, Object> resolveAccessTokenClaims(OAuth2AuthenticationToken authenticationToken, HttpServletRequest request) {
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
            log.warn("AddAuthHeader - Failed to decode access token", ex);
            return Collections.emptyMap();
        } catch (Exception ex) {
            log.warn("AddAuthHeader - Failed to resolve access token claims", ex);
            return Collections.emptyMap();
        }
    }

    private String buildUserHeader(Map<String, Object> claims) {
        ObjectNode jsonNode = JsonUtils.getObjectMapper().createObjectNode();

        String publicId = asString(claims.get("sub"));
        if (publicId != null) {
            jsonNode.put("publicId", publicId);
        }

        String userUuid = asString(claims.get(CLAIM_USER_ID));
        if (userUuid != null) {
            jsonNode.put("userUuid", userUuid);
        }

        Object rolesObj = claims.get(CLAIM_ROLES);
        if (rolesObj != null) {
            jsonNode.putPOJO(CLAIM_ROLES, rolesObj);
        }
        else {
            jsonNode.putArray(CLAIM_ROLES);
        }

        String tier = asString(claims.get(CLAIM_TIER));
        if (tier != null) {
            jsonNode.put(CLAIM_TIER, tier);
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonNode.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static class CloudAuthRequestWrapper extends HttpServletRequestWrapper {
        private final String cloudAuthHeaderValue;

        public CloudAuthRequestWrapper(HttpServletRequest request, String cloudAuthHeaderValue) {
            super(request);
            this.cloudAuthHeaderValue = cloudAuthHeaderValue;
        }

        @Override
        public String getHeader(String name) {
            if ("CLOUD_AUTH".equalsIgnoreCase(name)) {
                return cloudAuthHeaderValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("CLOUD_AUTH".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(cloudAuthHeaderValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.add("CLOUD_AUTH");
            return Collections.enumeration(names);
        }
    }
}