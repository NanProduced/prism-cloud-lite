package nan.produced.prism.auth.security.oauth;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = GoogleOAuthProps.PROPS_PREFIX)
public class GoogleOAuthProps {

    public static final String PROPS_PREFIX = "prism.security.google";

    /**
     * Google OAuth2 Client ID（前端获取 id_token 时的 client_id / aud 校验目标）。
     */
    private String clientId;

    /**
     * Google ID Token 的 JWKS 地址。
     */
    private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";

    /**
     * 允许的 issuer。
     */
    private List<String> issuers = List.of(
        "https://accounts.google.com",
        "accounts.google.com"
    );
}

