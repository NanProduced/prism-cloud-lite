package nan.produced.prism.core.integration.signature;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import nan.produced.prism.core.common.util.TraceUtils;

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
        String path = template.path();
        String signature = ServiceSignatureUtil.calculateSignature(template.method(), path, body, timestamp, properties.getSecret());

        template.header("X-Service-From", properties.getServiceFrom());
        template.header("X-Timestamp", String.valueOf(timestamp));
        template.header("X-Signature", signature);
        template.header("X-Trace-Id", TraceUtils.getTraceId());
    }

    private String extractBody(RequestTemplate template) {
        if (template.body() == null) {
            return "";
        }
        return new String(template.body(), StandardCharsets.UTF_8);
    }
}
