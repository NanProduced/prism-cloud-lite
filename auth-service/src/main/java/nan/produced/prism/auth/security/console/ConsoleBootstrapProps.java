package nan.produced.prism.auth.security.console;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.security.console.bootstrap")
public class ConsoleBootstrapProps {

    /**
     * Whether to bootstrap the initial global admin account on startup.
     */
    private boolean enabled = true;

    /**
     * Initial admin username (stored in pca_admin_users.email for v1).
     */
    private String username = "admin";

    /**
     * Initial admin password (bcrypt hashed at bootstrap time).
     */
    private String password = "Nan12091209";
}

