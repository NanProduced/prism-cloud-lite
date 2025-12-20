package nan.produced.prism.device.boot;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "nan.produced.prism.device")
@EnableJpaRepositories(basePackages = "nan.produced.prism.device.infrastructure.persistence.postgre.repository.jpa")
@EntityScan(basePackages = "nan.produced.prism.device.infrastructure.persistence.postgre.entity")
public class DeviceServiceApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(DeviceServiceApplication.class, args);
    }
}