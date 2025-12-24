package nan.produced.prism.core.message.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessageCenterProperties.class)
public class MessageCenterConfiguration {
}

