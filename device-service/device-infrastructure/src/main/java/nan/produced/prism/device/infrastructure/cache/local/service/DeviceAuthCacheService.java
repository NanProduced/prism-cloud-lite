package nan.produced.prism.device.infrastructure.cache.local.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.dto.cache.DeviceAuthCache;
import nan.produced.prism.device.application.port.outbound.auth.DeviceAuthCachePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAuthCacheService implements DeviceAuthCachePort {

    private final Cache<String, DeviceAuthCache> deviceAuthenticationCache;

    @Override
    public void cache(String accountName, DeviceAuthCache deviceAuthCache) {
        deviceAuthenticationCache.put(accountName, deviceAuthCache);
    }

    @Override
    public Optional<DeviceAuthCache> get(String accountName) {
        return Optional.ofNullable(deviceAuthenticationCache.getIfPresent(accountName));
    }

    @Override
    public void remove(String accountName) {
        deviceAuthenticationCache.invalidate(accountName);
    }

    @Override
    public void clearAll() {
        try {
            long sizeBefore = deviceAuthenticationCache.estimatedSize();
            log.info("DeviceAuthCache - 清空所有设备认证缓存 - 清理前大小: {}", sizeBefore);
            deviceAuthenticationCache.invalidateAll();
        } catch (Exception e) {
            log.error("DeiceAuthCache - 清空设备认证缓冲失败", e);
        }
    }
}
