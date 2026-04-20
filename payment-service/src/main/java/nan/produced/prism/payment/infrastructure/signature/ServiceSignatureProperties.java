package nan.produced.prism.payment.infrastructure.signature;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.security.service-signature")
public class ServiceSignatureProperties {

    private String secret = "NanProduced";
    private String serviceFrom = "payment-service";
}
