package nan.produced.prism.core.integration.auth.signature;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceSignatureFeignConfig {

    private final ServiceSignatureProperties properties;

    public ServiceSignatureFeignConfig(ServiceSignatureProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RequestInterceptor serviceSignatureRequestInterceptor() {
        return new ServiceSignatureRequestInterceptor(properties);
    }
}
