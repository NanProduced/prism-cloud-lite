package nan.produced.prism.core.integration.auth.signature;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.security.service-signature")
public class ServiceSignatureProperties {

    /**
     * 共享的 HMAC 密钥
     */
    private String secret;

    /**
     * X-Service-From 头，标识调用方
     */
    private String serviceFrom = "core-service";
}
