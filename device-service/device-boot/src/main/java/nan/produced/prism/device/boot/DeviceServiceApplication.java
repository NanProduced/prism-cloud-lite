package nan.produced.prism.device.boot;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "nan.produced.prism.device")
public class DeviceServiceApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(DeviceServiceApplication.class, args);
    }
}
