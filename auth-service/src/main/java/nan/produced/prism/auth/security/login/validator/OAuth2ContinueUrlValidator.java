package nan.produced.prism.auth.security.login.validator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OAuth2继续URL验证器
 * <p>
 * 用于验证OAuth2授权流程中提供的继续URL是否合法。
 * 验证包括URL格式、客户端ID匹配、响应类型以及主机名等。
 */
@Component
@RequiredArgsConstructor
public class OAuth2ContinueUrlValidator {

    private final SecurityProps securityProps;

    /**
     * 验证OAuth2继续URL的有效性
     * 
     * @param request HTTP请求对象，用于获取当前服务器信息
     * @param rawContinueUrl 原始继续URL字符串
     * @return 解码后的有效继续URL
     * @throws IllegalArgumentException 当URL无效或不满足验证条件时抛出
     */
    public String validate(HttpServletRequest request, String rawContinueUrl) {
        if (!StringUtils.hasText(rawContinueUrl)) {
            throw new IllegalArgumentException("continueUrl is required");
        }

        String decoded = URLDecoder.decode(rawContinueUrl, StandardCharsets.UTF_8);
        UriComponents components = UriComponentsBuilder.fromUriString(decoded).build();
        String path = components.getPath();
        if (!StringUtils.hasText(path) || !isAuthorizeEndpointPath(path)) {
            throw new IllegalArgumentException("continueUrl is invalid");
        }

        MultiValueMap<String, String> queryParams = components.getQueryParams();
        String requiredClientId = securityProps.getOauth2().getClient().getPrismGatewayClient().getClientId();
        if (!requiredClientId.equals(queryParams.getFirst("client_id"))) {
            throw new IllegalArgumentException("continueUrl client_id mismatch");
        }

        String responseType = queryParams.getFirst("response_type");
        if (!"code".equalsIgnoreCase(responseType)) {
            throw new IllegalArgumentException("continueUrl response_type mismatch");
        }

        validateHost(request, components.toUri());
        return decoded;
    }

    private boolean isAuthorizeEndpointPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        // Compatible with both direct auth-service (/oauth2/authorize) and gateway prefixed exposure (/auth/oauth2/authorize).
        return path.startsWith("/oauth2/authorize") || path.startsWith("/auth/oauth2/authorize");
    }

    /**
     * 验证URI主机名是否被允许
     * <p>
     * 检查URI中的主机名是否在允许的主机列表中，
     * 或者是否与当前服务器主机名匹配。
     * 
     * @param request HTTP请求对象，用于获取当前服务器名称
     * @param uri 要验证的URI对象
     */
    private void validateHost(HttpServletRequest request, URI uri) {
        if (!StringUtils.hasText(uri.getHost())) {
            return;
        }
        List<String> allowedHosts = securityProps.getLogin().getSpa().getAllowedHosts();
        boolean hostAllowed = allowedHosts.stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(uri.getHost()));
        boolean matchesCurrentHost = uri.getHost().equalsIgnoreCase(request.getServerName());
        if (!hostAllowed && !matchesCurrentHost) {
            throw new IllegalArgumentException("continueUrl host is not allowed");
        }
    }
}
