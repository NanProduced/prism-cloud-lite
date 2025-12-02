package nan.produced.prism.auth.security.signature;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    @Value("${prism.security.service-signature.secret:}")
    private String signatureSecret;

    @Override
    public void apply(RequestTemplate template) {
        try {
            if (signatureSecret == null || signatureSecret.isBlank()) {
                log.warn("服务签名密钥未配置，跳过签名");
                return;
            }

            long timestamp = System.currentTimeMillis();

            // 提取请求信息
            String method = template.method();
            String path = extractPath(template.url());
            String body = extractBody(template);

            // 计算签名
            String signature = ServiceSignatureUtil.calculateSignature(
                method, path, body, timestamp, signatureSecret
            );

            // 添加请求头
            template.header("X-Service-From", serviceId);
            template.header("X-Timestamp", String.valueOf(timestamp));
            template.header("X-Signature", signature);

            log.debug("为请求添加服务签名: path={}, signature={}", path, signature.substring(0, Math.min(10, signature.length())) + "...");
        } catch (Exception e) {
            log.error("为请求添加签名失败: {}", e.getMessage(), e);
            throw new RuntimeException("为请求添加签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 URL 中提取路径
     *
     * @param url 完整 URL
     * @return 路径部分
     */
    private String extractPath(String url) {
        if (url == null) {
            return "";
        }

        // 找到第一个 / 之后的内容（跳过 http:// 或 https://）
        int pathStart = url.indexOf("://");
        if (pathStart != -1) {
            int nextSlash = url.indexOf("/", pathStart + 3);
            if (nextSlash != -1) {
                return url.substring(nextSlash);
            }
        }

        return "";
    }

    /**
     * 从请求模板中提取 body
     *
     * @param template Feign 请求模板
     * @return body 内容（如果有）
     */
    private String extractBody(RequestTemplate template) {
        if (template.body() == null || template.body().length == 0) {
            return "";
        }

        return new String(template.body(), StandardCharsets.UTF_8);
    }
}
