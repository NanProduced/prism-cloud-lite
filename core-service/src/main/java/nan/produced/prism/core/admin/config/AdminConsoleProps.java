package nan.produced.prism.core.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.admin-console")
public class AdminConsoleProps {

    /**
     * Gateway Spring Session Redis namespace.
     * <p>
     * Gateway config example: {@code spring.session.redis.namespace=prism:gateway:session}
     * </p>
     */
    private String gatewaySessionRedisNamespace = "prism:gateway:session";
}

