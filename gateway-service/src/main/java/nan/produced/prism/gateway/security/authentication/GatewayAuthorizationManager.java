package nan.produced.prism.gateway.security.authentication;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.gateway.security.config.GatewaySecurityProps;
import nan.produced.prism.gateway.security.config.GatewaySecurityProps.ApiPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static nan.produced.prism.gateway.security.AuthClaimsConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewaySecurityProps securityProps;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        HttpServletRequest request = context.getRequest();
        String requestPath = request.getRequestURI();
        ApiPolicy policy = resolvePolicy(requestPath);
        if (policy == null) {
            log.warn("GatewayAuthorizationManager - no policy matched for path {}", requestPath);
            return new AuthorizationDecision(false);
        }

        Map<String, Object> claims = extractClaims(auth);
        boolean granted = evaluatePolicy(policy, claims, requestPath);
        if (!granted) {
            log.info("GatewayAuthorizationManager - access denied for path {} with realm={}, tier={} claims", requestPath,
                    claims.getOrDefault(CLAIM_REALM, "UNKNOWN"), claims.getOrDefault(CLAIM_TIER, "UNKNOWN"));
        }
        return new AuthorizationDecision(granted);
    }

    /**
     * 匹配策略
     * @param requestPath 请求路径
     * @return 策略
     */
    private ApiPolicy resolvePolicy(String requestPath) {
        List<ApiPolicy> policies = Optional.ofNullable(securityProps.getApiPolicies()).orElse(Collections.emptyList());
        return policies.stream()
                .filter(policy -> pathMatcher.match(policy.getPattern(), requestPath))
                .findFirst()
                .orElse(null);
    }

    /**
     * 验证策略
     * @param policy 策略
     * @param claims 用户声明
     * @param path 请求路径
     * @return 是否通过验证
     */
    private boolean evaluatePolicy(ApiPolicy policy,
                                   Map<String, Object> claims,
                                   String path) {

        // 判断请求主体
        ApiPolicy.Realm realm = ApiPolicy.Realm.fromClaim(claims.get(CLAIM_REALM));
        if (policy.getRealm() != realm) {
            log.debug("GatewayAuthorizationManager - realm mismatch for path {} (required={}, actual={})", path, policy.getRealm(), realm);
            return false;
        }

        // 验证用户角色
        Set<String> userRoles = new HashSet<>(extractStringSet(claims.get(CLAIM_ROLES)));
        if (!policy.getRoles().isEmpty() && Collections.disjoint(userRoles, policy.getRoles())) {
            log.debug("GatewayAuthorizationManager - role mismatch for path {} (required={}, actual={})", path, policy.getRoles(), userRoles);
            return false;
        }

        // 验证用户订阅
        String tier = Optional.ofNullable(claims.get(CLAIM_TIER)).map(Object::toString).orElse("FREE");
        if (!policy.getTier().matches(tier)) {
            log.debug("GatewayAuthorizationManager - tier mismatch for path {} (required={}, actual={})", path, policy.getTier(), tier);
            return false;
        }

        return true;
    }

    /**
     * 提取字符串集合
     * @param claimValue 声明值
     * @return 字符串集合
     */
    private Set<String> extractStringSet(Object claimValue) {
        if (claimValue == null) {
            return Collections.emptySet();
        }
        if (claimValue instanceof Collection<?> collection) {
            return collection.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    /**
     * 提取声明
     * @param authentication 认证信息
     * @return 声明
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
}

