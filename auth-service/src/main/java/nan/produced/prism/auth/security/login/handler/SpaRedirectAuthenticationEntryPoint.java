package nan.produced.prism.auth.security.login.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.utils.HttpUtils;
import nan.produced.prism.auth.utils.JsonUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * SPA重定向认证入口点
 * <p>
 * 当未认证的用户尝试访问受保护资源时，此类负责处理认证入口点逻辑。
 * 根据请求类型（AJAX或普通请求），将用户重定向到相应的处理流程。
 * 对于SPA（单页应用）前端，会将其重定向到登录页面，并保留原始请求URL以便登录后跳转。
 */
@Slf4j
@RequiredArgsConstructor
public class SpaRedirectAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProps securityProps;

    /**
     * 处理未认证请求的入口点方法
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param authException 认证异常信息
     * @throws IOException IO异常
     * @throws ServletException Servlet异常
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.OK.value());
            return;
        }

        if (isAjaxRequest(request)) {
            String respJson = JsonUtils.toJson(PrismLoginRespJson.builder()
                    .code("401")
                    .msg("Unauthorized")
                    .build());
            HttpUtils.responseJson(respJson, response);
            return;
        }

        String target = buildOriginalRequestUrl(request);
        String entryPage = resolveEntryPage(request);
        String redirectUrl = UriComponentsBuilder
                .fromUriString(entryPage)
                .queryParam(securityProps.getLogin().getSpa().getContinueParam(), target)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        log.debug("Redirecting unauthenticated request to SPA login: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private String resolveEntryPage(HttpServletRequest request) {
        if (request == null) {
            return securityProps.getLogin().getSpa().getEntryPage();
        }

        String path = request.getRequestURI();
        boolean isAuthorize = StringUtils.hasText(path)
                && (path.startsWith("/oauth2/authorize") || path.startsWith("/auth/oauth2/authorize"));
        if (!isAuthorize) {
            return securityProps.getLogin().getSpa().getEntryPage();
        }

        String clientId = request.getParameter("client_id");
        String consoleClientId = securityProps.getOauth2().getClient().getPrismConsoleClient().getClientId();
        if (StringUtils.hasText(clientId) && StringUtils.hasText(consoleClientId) && consoleClientId.equals(clientId)) {
            return securityProps.getLogin().getSpa().getAdminEntryPage();
        }
        return securityProps.getLogin().getSpa().getEntryPage();
    }

    /**
     * 判断是否为AJAX请求
     * <p>
     * 通过检查以下三个条件判断是否为AJAX请求：
     * 1. Accept头是否包含application/json
     * 2. Content-Type是否包含application/json
     * 3. X-Requested-With头是否为XMLHttpRequest
     * 
     * @param request HTTP请求对象
     * @return 如果是AJAX请求返回true，否则返回false
     */
    private boolean isAjaxRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (StringUtils.hasText(accept) && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String contentType = request.getContentType();
        if (StringUtils.hasText(contentType) && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return StringUtils.hasText(xRequestedWith) && "XMLHttpRequest".equalsIgnoreCase(xRequestedWith);
    }

    /**
     * 构建原始请求URL
     * <p>
     * 将请求URL与查询参数组合成完整的URL字符串
     * 
     * @param request HTTP请求对象
     * @return 完整的原始请求URL
     */
    private String buildOriginalRequestUrl(HttpServletRequest request) {
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        HostAndPort forwardedHost = parseHostAndPort(firstForwardedValue(request.getHeader("X-Forwarded-Host")));
        Integer forwardedPort = parsePort(firstForwardedValue(request.getHeader("X-Forwarded-Port")));

        String resolvedScheme = StringUtils.hasText(scheme) ? scheme : request.getScheme();
        String resolvedHost = StringUtils.hasText(forwardedHost.host) ? forwardedHost.host : request.getServerName();
        Integer resolvedPort = forwardedPort != null
                ? forwardedPort
                : (forwardedHost.port != null ? forwardedHost.port : request.getServerPort());

        if (looksInternalHost(resolvedHost)) {
            URI publicBase = resolvePublicBaseUri(request);
            if (publicBase != null && StringUtils.hasText(publicBase.getHost())) {
                if (StringUtils.hasText(publicBase.getScheme())) {
                    resolvedScheme = publicBase.getScheme();
                }
                resolvedHost = publicBase.getHost();
                if (publicBase.getPort() > 0) {
                    resolvedPort = publicBase.getPort();
                } else {
                    resolvedPort = defaultPortForScheme(resolvedScheme);
                }
            }
        }

        StringBuilder url = new StringBuilder();
        url.append(resolvedScheme).append("://").append(resolvedHost);
        if (resolvedPort != null && resolvedPort > 0 && !isDefaultPort(resolvedScheme, resolvedPort)) {
            url.append(':').append(resolvedPort);
        }
        url.append(request.getRequestURI());

        String queryString = request.getQueryString();
        if (StringUtils.hasText(queryString)) {
            url.append('?').append(queryString);
        }
        return url.toString();
    }

    private URI resolvePublicBaseUri(HttpServletRequest request) {
        String clientId = request != null ? request.getParameter("client_id") : null;
        String consoleClientId = securityProps.getOauth2().getClient().getPrismConsoleClient().getClientId();
        String configuredHost;
        if (StringUtils.hasText(clientId) && StringUtils.hasText(consoleClientId) && consoleClientId.equals(clientId)) {
            configuredHost = securityProps.getOauth2().getClient().getPrismConsoleClient().getHost();
        } else {
            configuredHost = securityProps.getOauth2().getClient().getPrismGatewayClient().getHost();
        }
        if (!StringUtils.hasText(configuredHost)) return null;
        try {
            return URI.create(configuredHost.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstForwardedValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) return null;
        String v = headerValue.trim();
        int comma = v.indexOf(',');
        return (comma >= 0 ? v.substring(0, comma) : v).trim();
    }

    private static HostAndPort parseHostAndPort(String hostValue) {
        if (!StringUtils.hasText(hostValue)) return new HostAndPort(null, null);
        String v = hostValue.trim();

        if (v.startsWith("[") && v.contains("]")) {
            return new HostAndPort(v, null);
        }

        int idx = v.lastIndexOf(':');
        if (idx > 0 && idx < v.length() - 1) {
            String maybePort = v.substring(idx + 1);
            Integer port = parsePort(maybePort);
            if (port != null) {
                return new HostAndPort(v.substring(0, idx), port);
            }
        }
        return new HostAndPort(v, null);
    }

    private static Integer parsePort(String input) {
        if (!StringUtils.hasText(input)) return null;
        String v = input.trim();
        try {
            int p = Integer.parseInt(v);
            if (p <= 0 || p > 65535) return null;
            return p;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int defaultPortForScheme(String scheme) {
        if (!StringUtils.hasText(scheme)) return 80;
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if (!StringUtils.hasText(scheme)) return port == 80;
        return ("https".equalsIgnoreCase(scheme) && port == 443) || ("http".equalsIgnoreCase(scheme) && port == 80);
    }

    private static boolean looksInternalHost(String host) {
        if (!StringUtils.hasText(host)) return true;
        String h = host.trim().toLowerCase();
        if ("localhost".equals(h) || "auth-service".equals(h) || "gateway-service".equals(h)) return true;
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) return true;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        return false;
    }

    private record HostAndPort(String host, Integer port) {
    }
}
