package nan.produced.prism.device.boot.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.infrastructure.internal.ApiResponse;
import nan.produced.prism.device.boot.security.DeviceSecurityProps;
import nan.produced.prism.device.common.exception.ErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.utils.SignatureUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 服务签名验证过滤器
 * 验证来自其他服务的 /internal/** 请求的 HMAC 签名（路径匹配由 {@link DeviceSecurityProps.InternalApi#pathPattern} 配置）
 * <p>
 * 验证流程：
 * 1. 检查请求路径是否匹配 internalApi.pathPattern（Ant 风格）
 * 2. 验证来源 IP 是否在白名单中
 * 3. 提取 X-Service-From、X-Timestamp、X-Signature 请求头
 * 4. 读取请求体并验证签名
 * 5. 验证时间戳是否在合理范围内（防止重放攻击）
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceSignatureValidatorFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final DeviceSecurityProps securityProps;

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String requestPath = resolveApplicationPath(request);

        // 只验证 /internal/** 路径（可配置）
        if (!isInternalApiPath(requestUri, requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 验证 IP 白名单
        String clientIp = getClientIp(request);
        if (!isIpWhitelisted(clientIp)) {
            log.warn("IP not whitelisted: {}", clientIp);
            sendErrorResponse(response, BusinessErrorCode.IP_NOT_WHITELISTED);
            return;
        }

        // 2. 提取签名相关请求头
        String serviceFrom = request.getHeader("X-Service-From");
        String timestampStr = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Signature");

        if (serviceFrom == null || timestampStr == null || signature == null) {
            log.warn("Missing required signature headers from IP: {}", clientIp);
            sendErrorResponse(response, BusinessErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        // 3. 验证时间戳（防止重放攻击）
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestampStr);
            sendErrorResponse(response, BusinessErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        long now = System.currentTimeMillis();
        long timestampToleranceMs = securityProps.getServiceSignature().getTimestampToleranceMs();
        if (Math.abs(now - timestamp) > timestampToleranceMs) {
            log.warn("Timestamp out of tolerance: {} ms difference", Math.abs(now - timestamp));
            sendErrorResponse(response, BusinessErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        // 4. 缓存 body 并包装 request：避免读取 body 后下游 @RequestBody 读不到
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        // 5. 读取请求体（用于签名校验）
        String body = wrappedRequest.getCachedBodyAsString();
        String method = request.getMethod();
        String path = requestPath;
        String contentType = request.getContentType();

        // 6. 验证签名
        String secret = securityProps.getServiceSignature().getSecret();
        boolean isValid = SignatureUtils.verifySignature(signature, method, path, body, timestamp, secret);
        // Backward/edge-case compatibility: if some upstream includes context-path in the signature calculation,
        // allow validating against the full request URI as well.
        if (!isValid && !requestUri.equals(path)) {
            isValid = SignatureUtils.verifySignature(signature, method, requestUri, body, timestamp, secret);
        }
        // Compatibility for callers that include raw query string in signature input.
        String queryString = request.getQueryString();
        if (!isValid && StringUtils.hasText(queryString)) {
            String uriWithQuery = requestUri + "?" + queryString;
            isValid = SignatureUtils.verifySignature(signature, method, uriWithQuery, body, timestamp, secret);
        }
        // Compatibility for Feign + non-JSON requests (e.g., @RequestParam on POST):
        // some callers may sign with empty body even if the transport uses form body; only relax for non-JSON payloads.
        boolean isJsonPayload = contentType != null && contentType.toLowerCase().contains("json");
        if (!isValid && !isJsonPayload && StringUtils.hasText(body)) {
            isValid = SignatureUtils.verifySignature(signature, method, path, "", timestamp, secret);
            if (!isValid && !requestUri.equals(path)) {
                isValid = SignatureUtils.verifySignature(signature, method, requestUri, "", timestamp, secret);
            }
            if (!isValid && StringUtils.hasText(queryString)) {
                String uriWithQuery = requestUri + "?" + queryString;
                isValid = SignatureUtils.verifySignature(signature, method, uriWithQuery, "", timestamp, secret);
            }
        }

        if (!isValid) {
            log.warn("Invalid service signature from service: {}, IP: {}, method: {}, uri: {}",
                    serviceFrom, clientIp, method, requestUri);
            sendErrorResponse(response, BusinessErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * 判断请求路径是否为内部 API
     */
    private boolean isInternalApiPath(String requestUri, String applicationPath) {
        String internalPathPattern = securityProps.getInternalApi().getPathPattern();
        if (internalPathPattern == null || internalPathPattern.isBlank()) {
            return false;
        }

        String pattern = internalPathPattern.trim();
        // Prefer matching against application path (without context-path), but keep backward compatibility
        // for configurations that include context-path in the pattern.
        return ANT_PATH_MATCHER.match(pattern, applicationPath) || ANT_PATH_MATCHER.match(pattern, requestUri);
    }

    /**
     * Resolve the path used for signature calculation and path matching.
     * <p>
     * {@link HttpServletRequest#getRequestURI()} includes the context-path, while Feign {@code template.path()}
     * (caller side) typically does not. To keep signatures stable when context-path is configured, we strip it.
     * </p>
     */
    private String resolveApplicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!StringUtils.hasText(uri) || !StringUtils.hasText(contextPath)) {
            return uri;
        }
        return uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }

    /**
     * 判断 IP 是否在白名单中
     */
    private boolean isIpWhitelisted(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }

        String ipWhitelist = securityProps.getInternalApi().getIpWhitelist();
        if (!StringUtils.hasText(ipWhitelist)) {
            return false;
        }

        String normalizedClientIp = clientIp.trim();

        return Arrays.stream(ipWhitelist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(entry -> isIpMatch(entry, normalizedClientIp));
    }

    private boolean isIpMatch(String whitelistEntry, String clientIp) {
        // Convenience alias. "localhost" should match loopback addresses.
        if ("localhost".equalsIgnoreCase(whitelistEntry)) {
            return "127.0.0.1".equals(clientIp) || "::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp);
        }

        try {
            return new IpAddressMatcher(whitelistEntry).matches(clientIp);
        } catch (Exception ex) {
            // Fallback to exact match if the entry is not a valid CIDR/IP expression.
            return whitelistEntry.equals(clientIp);
        }
    }

    /**
     * 获取客户端真实 IP
     * 考虑代理和负载均衡器
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int idx = ip.indexOf(',');
            if (idx > 0) {
                ip = ip.substring(0, idx);
            }
            return ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
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

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().getValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<?> apiResponse = ApiResponse.error(errorCode, errorCode.getMessage());
        String jsonResponse = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }


}
