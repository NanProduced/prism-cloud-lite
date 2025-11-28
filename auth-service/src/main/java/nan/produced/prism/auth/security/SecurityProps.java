package nan.produced.prism.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.Resource;

@Data
@RefreshScope
@ConfigurationProperties(prefix = SecurityProps.PROPS_PREFIX)
public class SecurityProps {

    public static final String PROPS_PREFIX = "prism.security";

    private Login login = new Login();

    private Oauth2 oauth2 = new Oauth2();

    private Jwt jwt = new Jwt();

    private Slo slo = new Slo();

    private WhiteList whiteList = new WhiteList();

    @Data
    public static class Login {

        /**
         * 登录页面URL(对应GET请求)
         */
        private String loginPageUrl = "/login";

        /**
         * 登录处理URL(对应POST请求)
         */
        private String loginProcessingUrl = "/login";

        private String loginSuccessUrl = "/";

    }

    @Data
    public static class Oauth2 {

        private Client client = new Client();

        @Data
        public static class Client {

            private PrismGatewayClient prismGatewayClient = new PrismGatewayClient();

            @Data
            public static class PrismGatewayClient {

                private String clientId = "prism-gateway-client";

                private String clientSecret = "NanProduced";

                private String host = "http://localhost:8082";

                private String redirectUri = host + "/login/oauth2/code/prism-gateway";

                private String logoutRedirectUri = host + "/logout-status";

                private String backchannelLogoutUri;

                private String scope = "openid";

                private Long accessTokenValidityMinutes = 30L;

                private Long refreshTokenValidityMinutes = 720L;

            }
        }
    }

    @Data
    public static class Jwt {

        private Resource rsaPublicKey;

        private Resource rsaPrivateKey;
    }

    @Data
    public static class Slo {

        private boolean enabled = true;

        private String defaultLogoutRedirectUri = "/logout_status";

    }

    @Data
    public static class WhiteList {

        /**
         * 忽略的URL
         */
        private String rsaPublicKey = "/rsa/publicKey";

        private String actuator = "/actuator/**";

        private String[] ignoreUrls = new String[]{
                "/",
                "/error",
                "/favicon.ico",
                "/**/*.html",
                "/**/*.css",
                "/**/*.js",
                "/**/*.png",
                "/**/*.jpg",
                "/**/*.jpeg",
                "/**/*.gif",
                "/**/*.svg",
                "/**/*.ico",
                "/**/*.ttf",
                "/**/*.woff",
        };
    }

}
