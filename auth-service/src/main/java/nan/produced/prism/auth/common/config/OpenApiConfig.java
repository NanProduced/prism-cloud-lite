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
                # Prism 认证服务 API 文档

                ## 功能概述
                提供用户认证、注册、OAuth2/OIDC 授权服务

                ## 用户注册流程
                1. **申请 OTP**：`POST /register/request-otp` - 向邮箱发送验证码
                2. **验证 OTP**：`POST /register/verify-otp` - 验证验证码，获取临时令牌
                3. **完成注册**：`POST /register/complete` - 提交密码和用户信息完成注册

                ## 响应格式
                所有接口统一使用 `BffResponse<T>` 格式：
                - `success`: 操作是否成功
                - `data`: 响应数据（成功时）
                - `error`: 错误详情（失败时）
                - `traceId`: 链路追踪ID

                ## 错误处理
                - 所有错误都包含错误码（格式：`领域-数字`）
                - `retryable` 字段指示是否可重试
                - `displayMessage` 为用户友好的错误提示
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