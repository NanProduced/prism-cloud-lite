package nan.produced.prism.core.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * core-service 内部接口安全配置。
 *
 * <p>内部接口用于微服务之间调用（例如 device-service 拉取设备节目/素材清单）。</p>
 */
@Data
@ConfigurationProperties(prefix = "prism.security.internal-api")
public class InternalApiSecurityProps {

    /**
     * 内部 API 路径匹配模式（Ant 风格）。
     */
    private String pathPattern = "/internal/**";

    /**
     * IP 白名单（逗号分隔）。
     *
     * <p>开发环境允许本机回环；生产环境建议配置为服务网段或网关出口 IP。</p>
     */
    private String ipWhitelist = "127.0.0.1,::1,localhost";

    /**
     * 时间戳容忍度（毫秒），防止重放攻击。默认 5 分钟。
     */
    private long timestampToleranceMs = 300000L;
}

