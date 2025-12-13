package nan.produced.prism.core.device.infrastructure.messaging.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 设备事件幂等性处理器
 * 防止重复处理同一条消息（基于 category + traceId + deviceId）
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventIdempotentHandler {

    private final StringRedisTemplate redisTemplate;

    // Redis key 前缀
    private static final String IDEMPOTENT_KEY_PREFIX = "device:event:idempotent:";

    // 幂等性记录保留时间（10分钟）
    private static final long IDEMPOTENT_EXPIRE_MINUTES = 10;

    /**
     * 检查消息是否已处理过
     *
     * @param category 事件分类
     * @param deviceId 设备ID
     * @param traceId 消息追踪ID
     * @return true 表示消息已处理过，应该跳过；false 表示首次处理
     */
    public boolean isDuplicate(String category, Long deviceId, String traceId) {
        if (deviceId == null || traceId == null) {
            return false;
        }

        String key = generateKey(category, deviceId, traceId);

        // 使用 Redis SET NX 原子操作
        // 如果 key 不存在则设置，返回 true；如果 key 已存在，返回 false
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1",
                IDEMPOTENT_EXPIRE_MINUTES, TimeUnit.MINUTES);

        if (result == null) {
            log.warn("DeviceEventIdempotent - Redis 操作异常: deviceId={}, traceId={}", deviceId, traceId);
            return false;
        }

        if (result) {
            return false;
        } else {
            log.warn("DeviceEventIdempotent - 检测到重复消息: deviceId={}, traceId={}", deviceId, traceId);
            return true;
        }
    }

    /**
     * 手动标记消息已处理（用于异常情况）
     *
     * @param deviceId 设备ID
     * @param traceId 消息追踪ID
     */
    public void markAsProcessed(String category, Long deviceId, String traceId) {
        if (deviceId == null || traceId == null) {
            return;
        }

        String key = generateKey(category, deviceId, traceId);
        redisTemplate.opsForValue().set(key, "1",
                IDEMPOTENT_EXPIRE_MINUTES, TimeUnit.HOURS);
        log.debug("DeviceEventIdempotent - 标记消息已处理: deviceId={}, traceId={}", deviceId, traceId);
    }

    /**
     * 删除幂等性记录（用于重试场景）
     *
     * @param category 事件分类
     * @param deviceId 设备ID
     * @param traceId 消息追踪ID
     */
    public void removeIdempotentRecord(String category, Long deviceId, String traceId) {
        if (deviceId == null || traceId == null) {
            return;
        }

        String key = generateKey(category, deviceId, traceId);
        redisTemplate.delete(key);
        log.debug("DeviceEventIdempotent - 删除幂等性记录（允许重试）: deviceId={}, traceId={}", deviceId, traceId);
    }

    /**
     * 生成幂等性 key
     *
     * @param category 事件分类
     * @param deviceId 设备ID
     * @param traceId 追踪ID
     * @return Redis key
     */
    private String generateKey(String category, Long deviceId, String traceId) {
        return IDEMPOTENT_KEY_PREFIX + deviceId + ":" + category + ":" + traceId;
    }
}
