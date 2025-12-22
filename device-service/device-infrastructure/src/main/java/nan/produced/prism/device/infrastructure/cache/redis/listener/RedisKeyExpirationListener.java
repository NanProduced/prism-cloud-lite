package nan.produced.prism.device.infrastructure.cache.redis.listener;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.CommonConstant;
import nan.produced.prism.device.application.domain.event.DeviceOnlineStatusEvent;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.outbound.command.DeviceCommandQueuePort;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis设备状态过期监听器
 * 监听当前数据库的设备状态键过期事件，计算在线时长
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKeyExpirationListener implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final PatternTopic keyExpirationTopic;
    private final DeviceOnlineStatusPort deviceOnlineStatusPort;
    private final DeviceCommandQueuePort deviceCommandQueuePort;
    private final DeviceEventPublisherPort deviceEventPublisherPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 设备状态键模式：device:status:123
     */
    private static final Pattern DEVICE_STATUS_PATTERN = Pattern.compile("^device:status:(\\d+)$");

    /**
     * 设备指令键模式：terminal:command:detail:123432467132434:113
     */
    private static final Pattern DEVICE_COMMAND_PATTERN = Pattern.compile("^terminal:command:detail:(\\d+):(\\d+)$");

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, keyExpirationTopic);
        log.info("RedisTTL监听 - 已注册到特定数据库的键过期事件监听: {}", keyExpirationTopic.getTopic());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {

        String expiredKey = message.toString();
        try {
            Matcher deviceStatusMatcher = DEVICE_STATUS_PATTERN.matcher(expiredKey);
            if (deviceStatusMatcher.matches()) {
                Long deviceId = Long.valueOf(deviceStatusMatcher.group(1));

                handleDeviceStatusExpiration(deviceId);
                return;
            }

            Matcher deviceCommandMatcher = DEVICE_COMMAND_PATTERN.matcher(expiredKey);
            if (deviceCommandMatcher.matches()) {
                Long deviceId = Long.valueOf(deviceCommandMatcher.group(1));
                Integer queueId = Integer.valueOf(deviceCommandMatcher.group(2));

                handleDeviceCommandExpiration(deviceId, queueId);
            }
        } catch (Exception e) {
            log.error("RedisTTL监听 - 处理设备状态过期失败: expiredKey={}", expiredKey, e);
        }

    }

    /*========================= 设备状态键过期处理 =========================*/

    /**
     * 处理设备状态过期
     */
    private void handleDeviceStatusExpiration(Long deviceId) {
        try {
            // 移除设备状态索引
            deviceOnlineStatusPort.removeDeviceIndex(deviceId);

            // 发布确认终端离线事件
            publishConfirmOfflineEvent(deviceId);


        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 处理设备状态过期失败: deviceId={}", deviceId, e);
        }
    }

    /**
     * 发布确认离线事件（缓存已过期，相关键已删）
     */
    private void publishConfirmOfflineEvent(Long deviceId) {
        try {
            // 发布设备离线事件
            eventPublisher.publishEvent(DeviceOnlineStatusEvent.createConfirmOfflineEvent(deviceId));

            log.info("RedisTTL监听 -deviceStatus- 设备状态TTL过期，已发布事件: deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("RedisTTL监听 -deviceStatus- 发布设备离线事件失败: deviceId={}", deviceId, e);
        }
    }

    /*========================= 设备指令键过期处理 =========================*/

    /**
     * 处理设备指令过期
     * @param deviceId 设备ID
     * @param queueId 指令ID
     */
    private void handleDeviceCommandExpiration(Long deviceId, Integer queueId) {
        // 从指令队列移除
        deviceCommandQueuePort.removeFromQueue(deviceId, queueId);
        Map<String, Object> payload = Map.of(CommonConstant.Command.QUEUE_ID, queueId);
        DeviceEventMessage eventMessage = DeviceEventMessage.builder()
                .deviceId(deviceId)
                .eventType(CommonConstant.Command.EXPIRED)
                .payload(payload)
                .retryable(true)
                .build();
        // 发送指令过期消息
        deviceEventPublisherPort.publishCommand(CommonConstant.Command.EXPIRED, eventMessage);
    }
}
