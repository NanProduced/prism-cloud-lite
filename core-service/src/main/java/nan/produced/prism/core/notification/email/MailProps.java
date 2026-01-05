package nan.produced.prism.core.notification.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.mail")
public class MailProps {

    /**
     * From address used for outbound emails. If blank, falls back to spring.mail.username.
     */
    private String from;

    /**
     * Optional display name for the From header.
     */
    private String fromName = "Prism Cloud Lite";

    /**
     * Brand/product display name used in templates.
     */
    private String productName = "Prism Cloud Lite";

    /**
     * User portal URL (used in customer-facing templates).
     */
    private String prismUrl = "https://prism.nanproduced.cloud";

    /**
     * Console URL (legacy, used in templates).
     *
     * @deprecated Use {@link #prismUrl} instead.
     */
    @Deprecated
    private String consoleUrl;
}
