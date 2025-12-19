package nan.produced.prism.core.integration.signature;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 服务签名请求拦截器
 * 为所有向其他服务的 Feign 请求添加服务签名
 * <p>
 * 添加的请求头：
 * - X-Service-From: 调用服务标识
 * - X-Timestamp: 请求时间戳
 * - X-Signature: HMAC-SHA256 签名
 */
public class ServiceSignatureRequestInterceptor implements RequestInterceptor {

    private final ServiceSignatureProperties properties;

    public ServiceSignatureRequestInterceptor(ServiceSignatureProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        long timestamp = System.currentTimeMillis();
        String body = extractBody(template);
        String path = normalizePath(template.path());
        String signature = ServiceSignatureUtil.calculateSignature(template.method(), path, body, timestamp, properties.getSecret());

        template.header("X-Service-From", properties.getServiceFrom());
        template.header("X-Timestamp", String.valueOf(timestamp));
        template.header("X-Signature", signature);
        template.header("X-Trace-Id", TraceUtils.getTraceId());

        propagateClientContext(template);
    }

    private String extractBody(RequestTemplate template) {
        if (template.body() == null) {
            return "";
        }
        return new String(template.body(), StandardCharsets.UTF_8);
    }

    /**
     * Keep signature compatible with auth-service validation which uses {@code HttpServletRequest#getRequestURI()}.
     * <p>
     * {@code getRequestURI()} excludes query strings, so we must do the same on the caller side.
     * </p>
     */
    private String normalizePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        int idx = rawPath.indexOf('?');
        return idx >= 0 ? rawPath.substring(0, idx) : rawPath;
    }

    /**
     * Propagate end-user client context (ip/ua/device) to internal calls for audit purposes.
     * <p>
     * This is best-effort and should never be used for authorization decisions.
     * </p>
     */
    private void propagateClientContext(RequestTemplate template) {
        if (template == null) {
            return;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return;
        }

        String clientIp = resolveClientIp(request);
        if (StringUtils.hasText(clientIp)) {
            template.header("X-Client-Ip", clientIp);
        }
        String userAgent = request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent)) {
            template.header("X-Client-User-Agent", userAgent);
        }
        String device = request.getHeader("X-Client-Device");
        if (StringUtils.hasText(device)) {
            template.header("X-Client-Device", device);
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
        return request.getRemoteAddr();
    }
}
