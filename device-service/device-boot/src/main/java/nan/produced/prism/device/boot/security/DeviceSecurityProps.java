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

    private ServiceSignature serviceSignature = new ServiceSignature();

    private InternalApi internalApi = new InternalApi();

    @Data
    public static class WhiteList {

        private List<String> ignores = List.of(
                "/actuator/**",          // 监控端点
                "/health",               // 健康检查
                "/swagger-ui/**",        // API文档
                "/v3/api-docs/**"        // API文档

        );
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
         * 开发环境：本地地址
         * 生产环境：应配置为实际的服务 IP 地址
         */
        private String ipWhitelist = "127.0.0.1,::1,localhost";
    }
}
