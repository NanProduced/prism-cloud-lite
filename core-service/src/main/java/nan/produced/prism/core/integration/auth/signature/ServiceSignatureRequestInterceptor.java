package nan.produced.prism.core.integration.auth.signature;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import nan.produced.prism.core.common.util.TraceUtils;

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
