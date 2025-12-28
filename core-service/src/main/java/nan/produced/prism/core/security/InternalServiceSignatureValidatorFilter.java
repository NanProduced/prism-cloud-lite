package nan.produced.prism.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.integration.signature.ServiceSignatureProperties;
import nan.produced.prism.core.integration.signature.ServiceSignatureUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部接口服务签名校验过滤器。
 *
 * <p>用于保护 core-service 的 /internal/** 端点，只允许可信服务（如 device-service）通过签名调用。</p>
 *
 * <p>校验流程：</p>
 * <ol>
 *   <li>路径匹配：仅拦截 internal-api.pathPattern</li>
 *   <li>IP 白名单（可选但推荐）</li>
 *   <li>校验签名头：X-Service-From / X-Timestamp / X-Signature</li>
 *   <li>校验时间戳窗口（防重放）</li>
 *   <li>按 method + path(getRequestURI) + body + timestamp 计算 HMAC-SHA256 并常时间比较</li>
 * </ol>
 *
 * <p>注意：为了在过滤器中读取 body 且不影响下游 @RequestBody，必须缓存 body 并包装 request。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class InternalServiceSignatureValidatorFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final ServiceSignatureProperties serviceSignatureProperties;
    private final InternalApiSecurityProps internalApiSecurityProps;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        if (!isInternalApiPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1) IP 白名单（避免 internal API 暴露后被外部直接调用）
        String clientIp = resolveClientIp(request);
        if (!isIpWhitelisted(clientIp)) {
            log.warn("InternalApi - IP not whitelisted: ip={}, path={}", clientIp, requestPath);
            sendUnauthorized(response, "IP_NOT_WHITELISTED");
            return;
        }

        // 2) 读取并缓存 body（必须在计算签名前完成；并保证下游可重复读取）
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String body = wrappedRequest.getCachedBodyAsString();

        // 3) 提取签名头
        String serviceFrom = request.getHeader("X-Service-From");
        String timestampStr = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Signature");
        if (!StringUtils.hasText(serviceFrom) || !StringUtils.hasText(timestampStr) || !StringUtils.hasText(signature)) {
            log.warn("InternalApi - missing signature headers: ip={}, path={}", clientIp, requestPath);
            sendUnauthorized(response, "MISSING_SIGNATURE_HEADERS");
            return;
        }

        // 4) 时间戳校验（防重放）
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            sendUnauthorized(response, "INVALID_TIMESTAMP");
            return;
        }

        long now = System.currentTimeMillis();
        long toleranceMs = internalApiSecurityProps.getTimestampToleranceMs();
        if (Math.abs(now - timestamp) > toleranceMs) {
            log.warn("InternalApi - timestamp out of tolerance: diffMs={}, path={}", Math.abs(now - timestamp), requestPath);
            sendUnauthorized(response, "TIMESTAMP_OUT_OF_TOLERANCE");
            return;
        }

        // 5) 签名校验
        String secret = serviceSignatureProperties.getSecret();
        if (!StringUtils.hasText(secret)) {
            log.error("InternalApi - service signature secret is not configured, path={}", requestPath);
            sendUnauthorized(response, "SIGNATURE_SECRET_NOT_CONFIGURED");
            return;
        }

        String method = request.getMethod();
        String expected = ServiceSignatureUtil.calculateSignature(method, requestPath, body, timestamp, secret);
        if (!constantTimeEquals(signature, expected)) {
            log.warn("InternalApi - invalid signature: from={}, ip={}, path={}", serviceFrom, clientIp, requestPath);
            sendUnauthorized(response, "INVALID_SIGNATURE");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isInternalApiPath(String path) {
        String pattern = internalApiSecurityProps.getPathPattern();
        if (!StringUtils.hasText(pattern)) {
            return path != null && path.startsWith("/internal/");
        }
        return pathMatcher.match(pattern, path);
    }

    private boolean isIpWhitelisted(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        String ipWhitelist = internalApiSecurityProps.getIpWhitelist();
        if (!StringUtils.hasText(ipWhitelist)) {
            return true;
        }

        String normalizedClientIp = clientIp.trim();
        return Arrays.stream(ipWhitelist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(entry -> isIpMatch(entry, normalizedClientIp));
    }

    private boolean isIpMatch(String whitelistEntry, String clientIp) {
        if ("localhost".equalsIgnoreCase(whitelistEntry)) {
            return "127.0.0.1".equals(clientIp) || "::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp);
        }

        try {
            return new IpAddressMatcher(whitelistEntry).matches(clientIp);
        } catch (Exception ex) {
            return whitelistEntry.equals(clientIp);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendUnauthorized(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.<Void>error(ErrorCode.UNAUTHORIZED, "Internal API unauthorized: " + reason)
                .withMeta(TraceUtils.getTraceId(), null, "core-service");
        objectMapper.writeValue(response.getWriter(), body);
        response.getWriter().flush();
    }

    /**
     * 常时间比较，避免时序攻击。
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * 缓存请求体的包装器：解决过滤器读取 body 后，下游 @RequestBody 读不到的问题。
     */
    static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        String getCachedBodyAsString() {
            if (cachedBody == null || cachedBody.length == 0) {
                return "";
            }
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody != null ? cachedBody : new byte[0]);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // sync read
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
