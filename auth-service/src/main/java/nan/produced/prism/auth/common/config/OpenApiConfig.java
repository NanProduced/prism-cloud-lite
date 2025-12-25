package nan.produced.prism.auth.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI 3 配置
 * <p>
 * 为 auth-service 提供前端友好的 API 文档
 * </p>
 * 访问地址：<a href="http://localhost:8081/auth/swagger-ui.html">http://localhost:8081/auth/swagger-ui.html</a>
 *
 * @author Nan
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(serverList())
            .components(securityComponents());
    }

    /**
     * API 基本信息
     */
    private Info apiInfo() {
        return new Info()
            .title("Prism Auth Service API")
            .description("""
                # Auth Service（账号与认证）对接重点

                - **面向 SPA 的接口（经由 Gateway）**：`/auth/register/**`、`/auth/login/**`、`/auth/oauth2/jwks`。
                - **OAuth2/OIDC（授权码流程）**：由 Gateway 发起登录（`/oauth2/authorization/prism-gateway`），SPA 不需要直接拼装 `/auth/oauth2/authorize` 请求。
                - **内部接口（/auth/internal/**）**：仅供服务间调用（例如 core-service），前端不要调用。
                - **响应体**：面向 SPA 的接口使用 `BffResponse<T>`；内部接口使用 `ApiResponse<T>`（用于服务间 RPC）。
                - **HTTP Method 策略**：对外仅开放 GET/POST；不暴露 PUT/PATCH/DELETE 等 Method（详见 `docs/specs/http-method-policy.md`）。

                此处比对接口字段更强调“如何使用”，便于前端和第三方快速定位关键步骤。
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("Nan Produced")
                .email("nan@produced.com")
                .url("https://produced.nan"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    }

    /**
     * 服务器配置
     */
    private List<Server> serverList() {
        return List.of(
            new Server()
                .url("http://localhost:8082/auth")
                .description("本地开发环境（经由 Gateway 访问，推荐）"),
            new Server()
                .url("http://localhost:8081/auth")
                .description("本地开发环境（直连 auth-service，仅后端调试）"),
            new Server()
                .url("https://api.nanproduced.cloud/auth")
                .description("生产环境（经由 api.nanproduced.cloud/auth 暴露）")
        );
    }

    /**
     * 安全配置组件
     */
    private Components securityComponents() {
        return new Components()
            .addSecuritySchemes("OAuth2", new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 授权码流程（通常由 Gateway 发起，SPA 不需要手动拼装 authorize/token 请求）")
                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                    .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow()
                        .authorizationUrl("http://localhost:8082/auth/oauth2/authorize")
                        .tokenUrl("http://localhost:8082/auth/oauth2/token")
                        .refreshUrl("http://localhost:8082/auth/oauth2/token")
                    )
                )
            )
            .addSecuritySchemes("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token")
            );
    }
}
