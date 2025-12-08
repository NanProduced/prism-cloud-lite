package nan.produced.prism.device.infrastructure.websocket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.Map;

/**
 * 设备Websocket协议的配置属性
 *
 * @author Nan
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = PrismWebsocketConfigProps.PROPS_PREFIX)
public class PrismWebsocketConfigProps {

    public static final String PROPS_PREFIX = "prism.websocket";

    private Connection connection = new Connection();

    private Protocol protocol = new Protocol();

    /**
     * 连接管理配置
     */
    @Data
    public static class Connection {

        /**
         * 是否启用连接清理
         */
        private boolean cleanupEnabled = true;

        /**
         * 连接清理间隔(毫秒)
         * 默认10分钟
         */
        private long cleanupInterval = 600_000;

        /**
         * 心跳超时阈值(毫秒)
         * 默认65秒，比WebSocket心跳间隔(55秒)多10秒容错
         */
        private long heartbeatTimeout = 65_000;

        /**
         * 是否启用紧急统计信息输出
         * 在连接数异常时输出详细调试信息
         */
        private boolean emergencyStatsEnabled = true;
    }

    /**
     * 协议版本配置 - 在配置文件动态配置以覆盖枚举类中的supported
     * <p>这里的配置项要和ProtocolVersion枚举里的版本对应</p>
     * @see nan.produced.prism.device.application.domain.websocket.ProtocolVersion
     */
    @Data
    public static class Protocol {

        /**
         * 协议版本支持配置
         * Key: 协议版本字符串 (如"1.0", "1.1")
         * Value: 是否支持该协议版本
         */
        private Map<String, Boolean> versions = Map.of(
                "1.0", true,   // 默认支持V1.0
                "1.1", true
        );

        /**
         * 检查指定协议版本是否被支持
         *
         * @param versionString 协议版本字符串
         * @param enumSupported 枚举类中的supported
         * @return 是否支持该协议版本，配置项中没有则默认使用枚举类中配置
         */
        public boolean isVersionSupported(String versionString, boolean enumSupported) {
            return versions.getOrDefault(versionString, enumSupported);
        }
    }
}
