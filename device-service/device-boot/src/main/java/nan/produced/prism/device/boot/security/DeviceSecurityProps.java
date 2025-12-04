package nan.produced.prism.device.boot.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

@Data
@RefreshScope
@ConfigurationProperties(prefix = DeviceSecurityProps.PROPS_PREFIX)
public class DeviceSecurityProps {

    public static final String PROPS_PREFIX = "prism.security";

    private String deviceApi = "/wp-json/**";

    private WhiteList whiteList = new WhiteList();

    @Data
    public static class WhiteList {

        private List<String> ignores = List.of(
                "/actuator/**",          // 监控端点
                "/health",               // 健康检查
                "/swagger-ui/**",        // API文档
                "/v3/api-docs/**"        // API文档

        );
    }
}
