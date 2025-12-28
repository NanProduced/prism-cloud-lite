package nan.produced.prism.auth.security.signature;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 服务签名验证过滤器
 * 验证来自其他服务的 /internal/** 请求的 HMAC 签名（路径匹配由 {@link SecurityProps.InternalApi#pathPattern} 配置）
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
public class ServiceSignatureValidationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final SecurityProps securityProps;

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 只验证 /internal/** 路径（可配置）
        if (!isInternalApiPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 验证 IP 白名单
        String clientIp = getClientIp(request);
        if (!isIpWhitelisted(clientIp)) {
            log.warn("IP not whitelisted: {}", clientIp);
            sendErrorResponse(response, ErrorCode.IP_NOT_WHITELISTED);
            return;
        }

        // 2. 提取签名相关请求头
        String serviceFrom = request.getHeader("X-Service-From");
        String timestampStr = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Signature");

        if (serviceFrom == null || timestampStr == null || signature == null) {
            log.warn("Missing required signature headers from IP: {}", clientIp);
            sendErrorResponse(response, ErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        // 3. 验证时间戳（防止重放攻击）
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestampStr);
            sendErrorResponse(response, ErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        long now = System.currentTimeMillis();
        long timestampToleranceMs = securityProps.getServiceSignature().getTimestampToleranceMs();
        if (Math.abs(now - timestamp) > timestampToleranceMs) {
            log.warn("Timestamp out of tolerance: {} ms difference", Math.abs(now - timestamp));
            sendErrorResponse(response, ErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        // 4. 包装请求（缓存 body，保证 Controller 仍可读取）
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        // 5. 读取请求体
        String body = wrappedRequest.getCachedBodyAsString();
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 6. 验证签名
        String secret = securityProps.getServiceSignature().getSecret();
        boolean isValid = ServiceSignatureUtil.verifySignature(
                signature, method, path, body, timestamp, secret
        );

        if (!isValid) {
            log.warn("Invalid service signature from service: {}, IP: {}, method: {}, uri: {}",
                    serviceFrom, clientIp, method, path);
            sendErrorResponse(response, ErrorCode.INVALID_SERVICE_TOKEN);
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * 判断请求路径是否为内部 API
     */
    private boolean isInternalApiPath(String path) {
        String internalPathPattern = securityProps.getInternalApi().getPathPattern();
        if (internalPathPattern == null || internalPathPattern.isBlank()) {
            return false;
        }
        return ANT_PATH_MATCHER.match(internalPathPattern.trim(), path);
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
        if ("localhost".equalsIgnoreCase(whitelistEntry)) {
            return "127.0.0.1".equals(clientIp) || "::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp);
        }

        try {
            return new IpAddressMatcher(whitelistEntry).matches(clientIp);
        } catch (Exception ex) {
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
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<?> apiResponse = ApiResponse.error(errorCode, errorCode.getMessage());
        String jsonResponse = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}
