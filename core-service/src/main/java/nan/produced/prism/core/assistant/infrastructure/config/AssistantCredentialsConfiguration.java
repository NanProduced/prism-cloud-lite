package nan.produced.prism.core.assistant.infrastructure.config;

import nan.produced.prism.core.assistant.infrastructure.crypto.AesGcmCryptoService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssistantCredentialsProperties.class)
public class AssistantCredentialsConfiguration {

    @Bean
    public AesGcmCryptoService aesGcmCryptoService(AssistantCredentialsProperties properties) {
        return new AesGcmCryptoService(properties.masterKey());
    }
}

