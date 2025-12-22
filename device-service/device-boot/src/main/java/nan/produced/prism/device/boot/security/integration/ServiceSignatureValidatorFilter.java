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
import nan.produced.prism.device.boot.integration.ApiResponse;
import nan.produced.prism.device.boot.security.DeviceSecurityProps;
import nan.produced.prism.device.common.exception.ErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.utils.SignatureUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 服务签名验证过滤器
 * 验证来自其他服务的 /internal/* 请求的 HMAC 签名
 * <p>
 * 验证流程：
 * 1. 检查请求路径是否匹配 /internal/*
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 只验证 /internal/* 路径
        if (!isInternalApiPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Validating service signature for internal API: {}", requestPath);

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
        String path = request.getRequestURI();

        // 6. 验证签名
        String secret = securityProps.getServiceSignature().getSecret();
        boolean isValid = SignatureUtils.verifySignature(
                signature, method, path, body, timestamp, secret
        );

        if (!isValid) {
            log.warn("Invalid service signature from service: {}, IP: {}", serviceFrom, clientIp);
            sendErrorResponse(response, BusinessErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        log.debug("Service signature validated successfully for service: {}", serviceFrom);
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * 判断请求路径是否为内部 API
     */
    private boolean isInternalApiPath(String path) {
        // 简单的通配符匹配
        String internalPathPattern = securityProps.getInternalApi().getPathPattern();
        String pattern = internalPathPattern.replace("**", ".*").replace("*", "[^/]*");
        return path.matches(pattern);
    }

    /**
     * 判断 IP 是否在白名单中
     */
    private boolean isIpWhitelisted(String clientIp) {
        String ipWhitelist = securityProps.getInternalApi().getIpWhitelist();
        Set<String> whitelist = new HashSet<>(Arrays.asList(ipWhitelist.split(",")));
        return whitelist.contains(clientIp.trim());
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
