package nan.produced.prism.auth.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI 3 配置
 * <p>
 * 为 auth-service 提供前端友好的 API 文档
 * </p>
 * 访问地址：<a href="http://localhost:8081/swagger-ui.html">http://localhost:8081/swagger-ui.html</a>
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
            .components(securityComponents())
            .addSecurityItem(new SecurityRequirement().addList("OAuth2"));
    }

    /**
     * API 基本信息
     */
    private Info apiInfo() {
        return new Info()
            .title("Prism Auth Service API")
            .description("""
                # Auth-Service 对接重点

                - **责任边界**：负责注册、账号激活、OAuth2/OIDC 授权以及 JWKS 公钥；登录凭证和业务 API 由 Gateway/Core 处理。
                - **访问路径**：浏览器只访问 `/auth/**` 下的注册与公钥接口，其它 OAuth2 流程统一由 Gateway 暴露 `/oauth2/authorization/prism-gateway`；8081 端口只在本地/测试环境允许直接访问。
                - **注册流程**：遵循“申请 OTP → 验证 OTP → 完成注册”的三步交互，接口返回 `BffResponse`，前端根据 `success` 与 `error.displayMessage` 做提示即可。
                - **集成建议**：
                    * 需要刷新 JWKS 时调用 `/auth/.well-known/jwks.json`；
                    * Gateway 与 Core 通过 `/internal/**` 完成 RPC，避免前端绕过；
                    * 本说明聚焦流程与协作要点，字段细节请打开具体接口查看。

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
                .url("http://localhost:8081")
                .description("本地开发环境 (auth-service)"),
            new Server()
                .url("http://localhost:8848")
                .description("Nacos 注册中心")
        );
    }

    /**
     * 安全配置组件
     */
    private Components securityComponents() {
        return new Components()
            .addSecuritySchemes("OAuth2", new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 授权码流程")
                .flows(new io.swagger.v3.oas.models.security.OAuthFlows()
                    .authorizationCode(new io.swagger.v3.oas.models.security.OAuthFlow()
                        .authorizationUrl("http://localhost:8081/oauth2/authorize")
                        .tokenUrl("http://localhost:8081/oauth2/token")
                        .refreshUrl("http://localhost:8081/oauth2/token")
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