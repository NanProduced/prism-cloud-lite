package nan.produced.prism.gateway.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

@Data
@RefreshScope
@ConfigurationProperties(prefix = GatewaySecurityProps.PROPS_PREFIX)
public class GatewaySecurityProps {

    public static final String PROPS_PREFIX = "prism.security";

    private Oauth2 oauth2 = new Oauth2();

    private WhiteList whiteList = new WhiteList();

    @Data
    public static class Oauth2 {

        /**
         * Spring Security client registrationId。
         */
        private String registrationId = "prism-gateway";

        private String loginEndpoint = "/oauth2/authorization/" + registrationId;

        private Client client = new Client();

        private AuthorizationServer authorizationServer = new AuthorizationServer();

        @Data
        public static class Client {

            private String clientId = "prism-gateway-client";

            private String clientSecret = "NanProduced";

            /** Gateway 对外访问的基础地址，用于拼接默认重定向。 */
            private String host = "http://localhost:8082";

            private String redirectUri = "http://localhost:8082/login/oauth2/code/prism-gateway";

            private String logoutUri = "/logout";

            private String logoutRedirectUri = "http://localhost:8082/logout-status";

            private String backchannelLogoutUri = "http://localhost:8082/logout/backchannel";

            private String scope = "openid,profile,email,prism.account,prism.session";
        }

        @Data
        public static class AuthorizationServer {

            private String issuerUri = "http://localhost:8081";

            private String authorizationEndpoint = "http://localhost:8081/oauth2/authorize";

            private String tokenEndpoint = "http://localhost:8081/oauth2/token";

            private String jwkSetEndpoint = "http://localhost:8081/oauth2/jwks";

            private String logoutEndpoint = "http://localhost:8081/logout";
        }
    }

    @Data
    public static class WhiteList {

        private List<String> urls = List.of(
                "/logout",
                "/logout_status"
        );
    }
}
