package nan.produced.prism.gateway.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@RefreshScope
@ConfigurationProperties(prefix = GatewaySecurityProps.PROPS_PREFIX)
public class GatewaySecurityProps {

    public static final String PROPS_PREFIX = "prism.security";

    private Oauth2 oauth2 = new Oauth2();

    private WhiteList whiteList = new WhiteList();

    private Cors cors = new Cors();

    private List<ApiPolicy> apiPolicies = new ArrayList<>(List.of(
            ApiPolicy.of("/api/sse/**", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.FREE_OR_ABOVE),
            // Better Upload: presigned URL endpoint used by SPA via Gateway
            ApiPolicy.of("/api/upload", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.FREE_OR_ABOVE),
            ApiPolicy.of("/api/upload/**", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.FREE_OR_ABOVE),
            // AI assistant chat (Vercel AI SDK-compatible)
            ApiPolicy.of("/api/chat", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.FREE_OR_ABOVE),
            ApiPolicy.of("/api/v1/admin/**", ApiPolicy.Realm.ADMIN, List.of("ROLE_ADMIN"), ApiPolicy.TierRequirement.FREE_OR_ABOVE),
            ApiPolicy.of("/api/v1/pro/**", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.PRO_ONLY),
            ApiPolicy.of("/api/v1/**", ApiPolicy.Realm.END_USER, List.of("ROLE_END_USER"), ApiPolicy.TierRequirement.FREE_OR_ABOVE)
    ));

    @Data
    public static class Oauth2 {

        /**
         * Spring Security client registrationId
         */
        private String registrationId = "prism-gateway";

        private String loginEndpoint = "/oauth2/authorization/" + registrationId;

        private Client client = new Client();

        private AuthorizationServer authorizationServer = new AuthorizationServer();

        @Data
        public static class Client {

            private String clientId = "prism-gateway-client";

            private String clientSecret = "NanProduced";

            /** Gateway host used for composing default callback URLs during local runs */
            private String host = "http://localhost:8082";

            private String redirectUri = "http://localhost:8082/login/oauth2/code/prism-gateway";

            private String logoutUri = "/logout";

            private String logoutRedirectUri = "http://localhost:8082/logout-status";

            private String backchannelLogoutUri = "http://localhost:8082/logout/backchannel";

            private String scope = "openid,email,prism.account,prism.session";
        }

        @Data
        public static class AuthorizationServer {

            private String issuerUri = "http://localhost:8081/auth";

            private String authorizationEndpoint = "http://localhost:8081/auth/oauth2/authorize";

            private String tokenEndpoint = "http://localhost:8081/auth/oauth2/token";

            private String jwkSetEndpoint = "http://localhost:8081/auth/oauth2/jwks";

            /**
             * OIDC RP-initiated logout endpoint (end_session_endpoint).
             */
            private String logoutEndpoint = "http://localhost:8081/auth/connect/logout";
        }
    }

    @Data
    public static class WhiteList {

        private List<String> urls = List.of(
                "/error",
                "/auth/**",
                "/logout",
                "/logout/backchannel",
                "/logout_status",
                "/logout-status",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/core-service/v3/api-docs"
        );
    }

    @Data
    public static class Cors {

        /**
         * Allowed browser origins for cross-site requests to the gateway API.
         * <p>
         * Must be an explicit list when {@code allowCredentials=true}.
         * </p>
         */
        private List<String> allowedOrigins = List.of(
                "http://localhost:5173"
        );
    }

    @Data
    public static class ApiPolicy {

        private String pattern;

        private Realm realm = Realm.END_USER;

        private List<String> roles = new ArrayList<>();

        private TierRequirement tier = TierRequirement.FREE_OR_ABOVE;

        public static ApiPolicy of(String pattern, Realm realm, List<String> roles, TierRequirement tier) {
            ApiPolicy policy = new ApiPolicy();
            policy.setPattern(pattern);
            policy.setRealm(realm);
            policy.setRoles(new ArrayList<>(roles));
            policy.setTier(tier);
            return policy;
        }

        public enum Realm {
            END_USER,
            ADMIN;

            public static Realm fromClaim(Object raw) {
                if (raw == null) {
                    return END_USER;
                }
                String value = raw.toString();
                return Arrays.stream(values())
                        .filter(realm -> realm.name().equalsIgnoreCase(value))
                        .findFirst()
                        .orElse(END_USER);
            }
        }

        public enum TierRequirement {
            FREE_OR_ABOVE {
                @Override
                public boolean matches(String actualTier) {
                    return true;
                }
            },
            PRO_ONLY {
                @Override
                public boolean matches(String actualTier) {
                    return "PRO".equalsIgnoreCase(actualTier);
                }
            };

            public abstract boolean matches(String actualTier);
        }
    }
}
