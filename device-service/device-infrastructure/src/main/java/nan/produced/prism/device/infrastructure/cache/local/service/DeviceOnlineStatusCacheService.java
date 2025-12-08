package nan.produced.prism.device.infrastructure.cache.local.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.dto.cache.DeviceOnlineStatusUpdateContext;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusCachePort;
import org.springframework.stereotype.Service;

/**
 * 设备状态缓存实现服务
 * 使用 Caffeine 构建本地缓存，替代分布式锁以缓解 Redis 竞争
 * <p>
 * 职责：
 * - 提供设备状态上下文的获取和管理接口
 * - 缓存配置由 CaffeineConfig 统一管理
 * - 驱逐回调在 CaffeineConfig 中注册，通过 ObjectProvider 调用应用层实现
 * <p>
 * 依赖关系：
 * - 缓存 Bean 由 CaffeineConfig 创建并管理
 * - CaffeineConfig 使用 ObjectProvider<DeviceStatusFlushCallback> 处理驱逐回调
 * - 避免了 service 层与应用层之间的循环依赖
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineStatusCacheService implements DeviceOnlineStatusCachePort {

    private final Cache<Long, DeviceOnlineStatusUpdateContext> deviceOnlineStatusUpdateContextCache;

    @Override
    public DeviceOnlineStatusUpdateContext getOrCreateContext(Long deviceId) {
        return deviceOnlineStatusUpdateContextCache.get(deviceId, id -> new DeviceOnlineStatusUpdateContext());
    }

    @Override
    public void invalidate(Long deviceId) {

        try {
            deviceOnlineStatusUpdateContextCache.invalidate(deviceId);
        } catch (Exception e) {
            log.warn("DeviceStatusCache - 驱逐设备缓存失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void clearAll() {
        try {
            long sizeBefore = deviceOnlineStatusUpdateContextCache.estimatedSize();
            log.info("DeviceStatusCache - 清空所有设备更新缓存: 清理前大小={}", sizeBefore);
            deviceOnlineStatusUpdateContextCache.invalidateAll();
        } catch (Exception e) {
            log.error("DeviceStatusCache - 清空所有缓存失败", e);
        }
    }
}
