package nan.produced.prism.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import nan.produced.prism.core.integration.signature.ServiceSignatureProperties;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "nan.produced.prism.core.integration")
@EnableConfigurationProperties(ServiceSignatureProperties.class)
@Modulithic(sharedModules = "common", systemName = "Prism Core Service")
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}