package nan.produced.prism.payment.infrastructure.paddle;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "paddle")
public class PaddleConfig {

    private String apiKey;
    private String clientId;
    private String webhookSecret;
    private String environment = "sandbox";
    private String baseUrl = "https://sandbox-api.paddle.com";

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }
}
