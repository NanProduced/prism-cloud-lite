package nan.produced.prism.device.infrastructure.cache.local.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.dto.cache.DeviceAuthCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nan.produced.prism.device.application.dto.cache.DeviceOnlineStatusUpdateContext;
import nan.produced.prism.device.application.port.outbound.config.DeviceConfigPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusFlushCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CaffeineConfig {

    private final DeviceConfigPort deviceConfigPort;
    private final ObjectProvider<DeviceOnlineStatusFlushCallback> flushCallbacks;

    /**
     * 认证缓存 - 缓存终端认证信息和权限
     * <p>适用场景: WebSocket连接认证、API认证检查</p>
     *
     * @return 认证信息缓存实例
     */
    @Bean("deviceAuthenticationCache")
    public Cache<String, DeviceAuthCache> deviceAuthenticationCache() {
        return Caffeine.newBuilder()
                .maximumSize(20_000L)
                .expireAfterWrite(Duration.ofMinutes(30))
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats() // 配合 Spring Boot Actuator + Prometheus 使用，在 Grafana 上监控缓存的命中率（Hit Rate）
                .build();

    }

    /**
     * 设备状态更新上下文缓存 - 本地状态机替代 Redis 分布式锁
     *
     * <p>这里不配置@RefreshScope自动刷新，避免缓存数据被清空</p>
     * @return 设备状态更新上下文缓存实例
     */
    @Bean("deviceOnlineStatusUpdateContextCache")
    public Cache<Long, DeviceOnlineStatusUpdateContext> deviceOnlineStatusUpdateContextCache() {
        long expireAfterMs = Math.max(deviceConfigPort.getDeviceConfig().getOnlineStatus().getOfflineThreshold(), 60_000L);
        long maxEntries = Math.max(deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getMaxSize() * 4L, 20_000L);

        return Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterAccess(Duration.ofMillis(expireAfterMs))
                .recordStats()
                .removalListener((Long deviceId, DeviceOnlineStatusUpdateContext context, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    // 当缓存条目被驱逐时，尝试 flush 待处理状态
                    if (context.tryScheduleFlush()) {
                        // 通过 ObjectProvider 获取回调，避免循环依赖
                        flushCallbacks.ifAvailable(callback -> {
                            try {
                                callback.onContextEvicted(deviceId, context);
                                log.debug("设备状态缓存驱逐成功 flush: deviceId={}", deviceId);
                            } catch (Exception e) {
                                log.error("设备状态缓存驱逐时 flush 失败: deviceId={}", deviceId, e);
                            }
                        });
                    }
                })
                .build();
    }
}
