package nan.produced.prism.device.boot.security.integration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.boot.security.DeviceSecurityProps;
import nan.produced.prism.device.common.exception.business.BusinessException;
import nan.produced.prism.device.common.utils.SignatureUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.net.URI;

import static nan.produced.prism.device.common.exception.business.BusinessErrorCode.SYSTEM_ERROR;

/**
 * 服务签名请求拦截器
 * 为所有向 Core-Service 的 Feign 请求添加服务签名
 * <p>
 * 添加的请求头：
 * - X-Service-From: 调用服务标识
 * - X-Timestamp: 请求时间戳
 * - X-Signature: HMAC-SHA256 签名
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceSignatureInterceptor implements RequestInterceptor {

    @Value("${spring.application.name}")
    private String serviceId;

    private final DeviceSecurityProps securityProps;

    @Override
    public void apply(RequestTemplate template) {
        try {
            String signatureSecret = securityProps.getServiceSignature().getSecret();
            if (signatureSecret == null || signatureSecret.isBlank()) {
                log.warn("服务签名密钥未配置，跳过签名");
                return;
            }

            long timestamp = System.currentTimeMillis();

            // 提取请求信息
            String method = template.method();
            String path = resolveSigningPath(template);
            String body = extractBody(template);

            // 计算签名
            String signature = SignatureUtils.calculateSignature(
                    method, path, body, timestamp, signatureSecret
            );

            // 添加请求头
            template.headers().remove("X-Service-From");
            template.headers().remove("X-Timestamp");
            template.headers().remove("X-Signature");
            template.header("X-Service-From", serviceId);
            template.header("X-Timestamp", String.valueOf(timestamp));
            template.header("X-Signature", signature);

            log.debug("为请求添加服务签名: path={}, signature={}", path, signature.substring(0, Math.min(10, signature.length())) + "...");
        } catch (Exception e) {
            log.error("为请求添加签名失败: {}", e.getMessage(), e);
            throw new BusinessException(SYSTEM_ERROR, "为请求添加签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 服务端使用 HttpServletRequest#getRequestURI() 做签名校验（不包含 query string）。
     * 这里必须严格对齐：只签 path（不签 host/scheme/query），否则会出现 INVALID_SIGNATURE。
     */
    private String resolveSigningPath(RequestTemplate template) {
        if (template == null) {
            return "";
        }

        String rawUrl = template.url();
        String rawPath = template.path();
        Target<?> feignTarget = template.feignTarget();

        String raw = (rawUrl != null && !rawUrl.isBlank()) ? rawUrl : rawPath;
        if (raw == null || raw.isBlank()) {
            return "";
        }

        // Spring Cloud OpenFeign 在部分场景下 template.url/path 仍是相对路径，补齐 target baseUrl 以获得最终请求 URI。
        String targetUrl = feignTarget != null ? feignTarget.url() : null;
        if (targetUrl != null && !targetUrl.isBlank() && !raw.contains("://") && !targetUrl.contains("{")) {
            raw = joinUrl(targetUrl, raw);
        }

        // 去掉 query（服务端 getRequestURI() 不包含 query）
        int queryIdx = raw.indexOf('?');
        if (queryIdx >= 0) {
            raw = raw.substring(0, queryIdx);
        }

        // 绝对 URL 只取 path
        try {
            if (raw.contains("://")) {
                URI uri = URI.create(raw);
                if (uri.getPath() != null && !uri.getPath().isBlank()) {
                    return normalizeLeadingSlash(uri.getPath());
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        // 兜底：手动去掉 scheme/host
        int schemeIdx = raw.indexOf("://");
        if (schemeIdx >= 0) {
            int slashIdx = raw.indexOf('/', schemeIdx + 3);
            if (slashIdx >= 0) {
                return normalizeLeadingSlash(raw.substring(slashIdx));
            }
            return "/";
        }

        return normalizeLeadingSlash(raw);
    }

    private String joinUrl(String baseUrl, String relativeUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return relativeUrl;
        }
        if (relativeUrl == null || relativeUrl.isBlank()) {
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

    private String normalizeLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String extractBody(RequestTemplate template) {
        if (template.body() == null) {
            return "";
        }
        return new String(template.body(), StandardCharsets.UTF_8);
    }
}
