package nan.produced.prism.core.export.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExportProperties.class)
public class ExportConfiguration {
}

