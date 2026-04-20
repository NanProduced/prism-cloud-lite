package nan.produced.prism.payment.infrastructure.signature;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import nan.produced.prism.payment.common.util.TraceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ServiceSignatureRequestInterceptor implements RequestInterceptor {

    private final ServiceSignatureProperties properties;

    public ServiceSignatureRequestInterceptor(ServiceSignatureProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        long timestamp = System.currentTimeMillis();
        String body = extractBody(template);
        String rawPath = template.path();
        String rawUrl = template.url();
        Target<?> feignTarget = template.feignTarget();
        String path = normalizePath(resolveSigningPath(rawUrl, rawPath, feignTarget));
        String signature = ServiceSignatureUtil.calculateSignature(template.method(), path, body, timestamp, properties.getSecret());

        template.headers().remove("X-Service-From");
        template.headers().remove("X-Timestamp");
        template.headers().remove("X-Signature");
        template.headers().remove("X-Trace-Id");

        template.header("X-Service-From", properties.getServiceFrom());
        template.header("X-Timestamp", String.valueOf(timestamp));
        template.header("X-Signature", signature);
        template.header("X-Trace-Id", TraceUtils.getTraceId());

        propagateClientContext(template);
    }

    private String resolveSigningPath(String rawUrl, String rawPath, Target<?> feignTarget) {
        String raw = StringUtils.hasText(rawUrl) ? rawUrl : rawPath;
        if (!StringUtils.hasText(raw)) {
            return "";
        }

        String targetUrl = feignTarget != null ? feignTarget.url() : null;
        if (StringUtils.hasText(targetUrl) && !raw.contains("://") && !targetUrl.contains("{")) {
            raw = joinUrl(targetUrl, raw);
        }

        int queryIdx = raw.indexOf('?');
        if (queryIdx >= 0) {
            raw = raw.substring(0, queryIdx);
        }

        try {
            if (raw.contains("://")) {
                URI uri = URI.create(raw);
                if (StringUtils.hasText(uri.getPath())) {
                    return uri.getPath();
                }
            }
        } catch (Exception ignored) {
        }

        int schemeIdx = raw.indexOf("://");
        if (schemeIdx >= 0) {
            int slashIdx = raw.indexOf('/', schemeIdx + 3);
            if (slashIdx >= 0) {
                return raw.substring(slashIdx);
            }
            return "/";
        }

        return raw.startsWith("/") ? raw : "/" + raw;
    }

    private String joinUrl(String baseUrl, String relativeUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return relativeUrl;
        }
        if (!StringUtils.hasText(relativeUrl)) {
            return baseUrl;
        }
        if ("/".equals(relativeUrl)) {
            return baseUrl;
        }
        if (relativeUrl.startsWith("/?")) {
            return baseUrl + "?" + relativeUrl.substring(2);
        }
        if (relativeUrl.startsWith("?")) {
            return baseUrl + relativeUrl;
        }
        boolean baseEndsWithSlash = baseUrl.endsWith("/");
        boolean relStartsWithSlash = relativeUrl.startsWith("/");
        if (baseEndsWithSlash && relStartsWithSlash) {
            return baseUrl + relativeUrl.substring(1);
        }
        if (!baseEndsWithSlash && !relStartsWithSlash) {
            return baseUrl + "/" + relativeUrl;
        }
        return baseUrl + relativeUrl;
    }

    private String extractBody(RequestTemplate template) {
        if (template.body() == null) {
            return "";
        }
        return new String(template.body(), StandardCharsets.UTF_8);
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        int idx = rawPath.indexOf('?');
        return idx >= 0 ? rawPath.substring(0, idx) : rawPath;
    }

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
