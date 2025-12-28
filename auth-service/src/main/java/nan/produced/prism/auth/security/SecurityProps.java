package nan.produced.prism.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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
         * 前端控制登录页面
         */
        private Boolean spaLoginPage = true;

        /**
         * 登录页面URL(对应GET请求)
         */
        private String loginPageUrl = "/login";

        /**
         * 登录处理URL(对应POST请求)
         */
        private String loginProcessingUrl = "/login";

        private String loginSuccessUrl = "/";

        private Spa spa = new Spa();

        private RememberMe rememberMe = new RememberMe();

        @Data
        public static class Spa {

            /**
             * 前端 SPA 登录页完整 URL
             */
            private String entryPage = "http://localhost:5173/auth-form";

            /**
             * SPA 登录页使用的 continue 参数名
             */
            private String continueParam = "continue";

            /**
             * 允许 CORS 的前端来源
             */
            private List<String> allowedOrigins = List.of(
                    "http://localhost:5173");

            /**
             * 允许的 continueUrl host（避免开放重定向）
             */
            private List<String> allowedHosts = List.of(
                    "http://localhost:5173",
                    "http://localhost:8081");
        }

        @Data
        public static class RememberMe {

            /**
             * 是否启用 remember-me 逻辑。
             */
            private boolean enabled = true;

            /**
             * Cookie 名称。
             */
            private String cookieName = "prism-remember-me";

            /**
             * Cookie 和 token 的有效天数。
             */
            private long validityDays = 30;

            /**
             * Cookie Path。
             */
            private String cookiePath = "/";

            /**
             * 是否仅通过 HTTPS 发送 Cookie。
             */
            private boolean secureCookie = true;
        }

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

                /**
                 * RP-initiated logout whitelist (OIDC post_logout_redirect_uri).
                 * <p>
                 * If configured, takes precedence over {@link #logoutRedirectUri}. When empty, falls back to:
                 * <ul>
                 *   <li>{@code logoutRedirectUri} (if present)</li>
                 *   <li>{@code {host}/logout-status} and {@code {host}/logout_status}</li>
                 * </ul>
                 */
                private List<String> postLogoutRedirectUris;

                private String backchannelLogoutUri;

                private String scope = "openid,email,prism.account,prism.session";

                private Long accessTokenValidityMinutes = 30L;

                private Long refreshTokenValidityMinutes = 720L;

                public List<String> resolvePostLogoutRedirectUris() {
                    if (postLogoutRedirectUris != null && !postLogoutRedirectUris.isEmpty()) {
                        return postLogoutRedirectUris;
                    }

                    Set<String> dedup = new LinkedHashSet<>();
                    if (StringUtils.hasText(logoutRedirectUri)) {
                        dedup.add(logoutRedirectUri.trim());
                    }
                    if (StringUtils.hasText(host)) {
                        String normalizedHost = host.trim();
                        dedup.add(normalizedHost + "/logout-status");
                        dedup.add(normalizedHost + "/logout_status");
                    }
                    return new ArrayList<>(dedup);
                }

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
                "/internal/**",
                "/login/**",    // 登录接口
                "/register/**"  // 注册接口公开访问（JIT Provisioning 模式）
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
        private String secret = "NanProduced";

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
         * 开发环境：本地地址 + 常见私有网段（便于 Docker/Compose 内部调用）
         * 生产环境：应配置为实际的服务 IP 地址
         */
        private String ipWhitelist = "127.0.0.1,::1,localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";
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
