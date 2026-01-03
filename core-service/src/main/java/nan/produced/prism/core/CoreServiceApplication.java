package nan.produced.prism.core;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import nan.produced.prism.core.integration.signature.ServiceSignatureProperties;
import nan.produced.prism.core.admin.config.AdminConsoleProps;
import nan.produced.prism.core.notification.email.MailProps;
import nan.produced.prism.core.security.InternalApiSecurityProps;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "nan.produced.prism.core.integration")
@EnableConfigurationProperties({ServiceSignatureProperties.class, InternalApiSecurityProps.class, MailProps.class, AdminConsoleProps.class})
@Modulithic(sharedModules = "common", systemName = "Prism Core Service")
@EnableScheduling
public class CoreServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
