package nan.produced.prism.gateway.security.filter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.gateway.utils.JsonUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Slf4j
@Order(1)
public class AddAuthHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> claims = extractClaims(authentication);

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

    /**
     * 提取claims
     * @param authentication
     * @return
     */
    private Map<String, Object> extractClaims(Authentication authentication) {

        return switch (authentication) {

            // Bearer Token API调用
            case JwtAuthenticationToken jwt -> jwt.getToken().getClaims();


            // OIDC Login 前端SPA调用
            case OAuth2AuthenticationToken oidc when oidc.getPrincipal() instanceof DefaultOidcUser oidcUser ->
                    oidcUser.getClaims();

            case null, default -> Collections.emptyMap();
        };

    }

    private String buildUserHeader(Map<String, Object> claims) {
        ObjectNode jsonNode = JsonUtils.getObjectMapper().createObjectNode();

        jsonNode.put("publicId", claims.get("sub").toString());
        // todo: 其他信息

        return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonNode.toString().getBytes(StandardCharsets.UTF_8));
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
            List<String> names =Collections.list(super.getHeaderNames());
            names.add("CLOUD_AUTH");
            return Collections.enumeration(names);
        }
    }
}
