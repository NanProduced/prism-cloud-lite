package nan.produced.prism.core.device.application.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRefreshSignalPublisher {

    private static final String TYPE_DEVICE_UPDATED = "device.updated";

    private final RabbitMessagePublisher rabbitMessagePublisher;

    private final StringRedisTemplate redisTemplate;

    @Value("${prism.sse.device-updated.debounce-ms:2000}")
    private long debounceMs;

    /**
     * best-effort：刷新信号发布失败不应影响主业务流程。
     */
    public void publishDeviceUpdated(UUID userId, Long deviceId, Set<String> fields) {
        if (userId == null || deviceId == null) {
            return;
        }

        try {
            if (!acquireDebounceToken(userId, deviceId)) {
                return;
            }

            Map<String, Object> data = new HashMap<>();
            if (fields != null && !fields.isEmpty()) {
                data.put("fields", List.copyOf(fields));
            }

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(TYPE_DEVICE_UPDATED)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(userId)
                            .deviceId(deviceId)
                            .build())
                    .data(data)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_DEVICE_UPDATED, message);
        } catch (Exception ex) {
            log.warn("device.updated publish failed (ignored): userId={}, deviceId={}", userId, deviceId, ex);
        }
    }

    private boolean acquireDebounceToken(UUID userId, Long deviceId) {
        long ms = debounceMs;
        if (ms <= 0) {
            return true;
        }

        String key = "sse:device.updated:" + userId + ":" + deviceId;
        Duration ttl = Duration.ofMillis(ms);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }
}
