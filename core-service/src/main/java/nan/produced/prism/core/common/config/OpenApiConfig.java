package nan.produced.prism.core.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Prism Core Service API")
                        .description("""
                                # 对接提示

                                - 所有面向前端的接口都位于 `/api/v1/**`，必须由 Gateway 代理访问；
                                - 开发/测试可在 `http://localhost:8082/swagger-ui.html` 选择 **Core Service API**，查看由 Gateway 聚合的文档；
                                - 直接访问 `http://localhost:8080/v3/api-docs` 仅用于后端调试，线上环境禁止绕过 Gateway；
                                - 返回体统一为 `BffResponse<T>`，具体字段说明见《frontend-api-guide》以及 `service-standards.md`。
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Prism Platform")
                                .url("https://prism-cloud-lite.example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Gateway 聚合入口"),
                        new Server().url("http://localhost:8080").description("Core Service 本地调试端口")))
                .components(new Components());
    }
}

