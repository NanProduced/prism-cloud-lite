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

    private ServiceSignature serviceSignature = new ServiceSignature();

    private InternalApi internalApi = new InternalApi();

    private VerificationToken verificationToken = new VerificationToken();

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
        private String rsaPublicKey = "/oauth2/jwks";

        private String actuator = "/actuator/**";

        private String[] ignoreUrls = new String[]{
                "/",
                "/error",
                "/auth/register/**"  // 注册接口公开访问（JIT Provisioning 模式）
        };

        private String[] swagger = new String[]{
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/swagger-ui/**"
        };
    }

    @Data
    public static class ServiceSignature {

        /**
         * HMAC-SHA256 共享密钥（与其他服务必须相同）
         * 生产环境应通过环境变量或密钥管理服务配置
         */
        private String secret = "your-shared-secret-key-change-in-production";

        /**
         * 时间戳容忍度（毫秒），防止重放攻击
         * 默认 5 分钟
         */
        private long timestampToleranceMs = 300000L;
    }

    @Data
    public static class InternalApi {

        /**
         * 内部 API 路径匹配模式
         * 支持 Ant 风格通配符
         */
        private String pathPattern = "/internal/**";

        /**
         * IP 白名单（逗号分隔）
         * 开发环境：本地地址
         * 生产环境：应配置为实际的服务 IP 地址
         */
        private String ipWhitelist = "127.0.0.1,::1,localhost";
    }

    @Data
    public static class VerificationToken {

        /**
         * 验证令牌有效期（分钟）
         * 用于 OTP 验证通过后生成的临时令牌，用户需在有效期内完成注册
         * 默认 30 分钟
         */
        private long validityMinutes = 30L;

        /**
         * Redis key 前缀
         * 格式：auth:verification_token:{email}
         */
        private String keyPrefix = "auth:verification_token:";
    }

}
