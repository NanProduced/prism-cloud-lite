package nan.produced.prism.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "common", systemName = "Prism Core Service")
public class PrismCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrismCoreApplication.class, args);
    }
}