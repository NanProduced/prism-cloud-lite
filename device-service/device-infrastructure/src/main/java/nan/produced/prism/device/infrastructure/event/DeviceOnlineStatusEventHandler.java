package nan.produced.prism.device.infrastructure.event;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.CommonConstant;
import nan.produced.prism.device.application.domain.event.DeviceOnlineStatusEvent;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.application.port.outbound.status.DeviceLoginUpdatePort;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static nan.produced.prism.device.application.domain.CommonConstant.Device.*;

/**
 * 设备状态事件处理器
 * <p>
 * 处理设备状态变更事件，包括登录时间更新
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOnlineStatusEventHandler {

    private final DeviceAccountRepository deviceAccountRepository;

    private final DeviceLoginUpdatePort deviceLoginUpdatePort;

    private final DeviceEventPublisherPort deviceEventPublisherPort;

    @Async
    @EventListener
    public void handleDeviceOnlineStatusEvent(DeviceOnlineStatusEvent event) {
        if (event == null || event.getEventType() == null) {
            log.warn("DeviceStatusEvent - 收到空事件或事件类型为空，忽略: event={}", event);
            return;
        }
        switch (event.getEventType()) {
            case DEVICE_GO_LIVE -> processDeviceOnline(event);
            case DEVICE_RECONNECT -> processDeviceReconnect(event);
            case DEVICE_DETECTED_OFFLINE -> processDetectedDeviceOffline(event);
            case DEVICE_CONFIRMED_OFFLINE -> processConfirmDeviceOffline(event);
            case DEVICE_HEARTBEAT -> processHeartbeat(event);
            default -> {
                // 默认情况不处理
            }
        }
    }

    // ==================== 设备在线状态事件分类 ====================

    /**
     * 处理设备上线事件
     * 设备首次上线时立即更新登录时间到MySQL，确保firstLoginTime不丢失
     * @param event 设备上线事件
     */
    private void processDeviceOnline(DeviceOnlineStatusEvent event) {
        log.debug("DeviceStatusEvent - 设备上线事件: deviceId={}, source={}, clientIp={}",
                event.getDeviceId(), event.getReportSource(), event.getClientIp());
        // 推送设备上线事件(Mq)
        pushDeviceOnline(event);
        // 首次上线立即更新登录时间，确保firstLoginTime不丢失
        updateLoginTimeImmediate(event);

    }

    /**
     * 处理设备重连事件
     * 设备重连时提交到异步缓冲池批量更新
     * @param event 设备重连事件
     */
    private void processDeviceReconnect(DeviceOnlineStatusEvent event) {
        log.debug("DeviceStatusEvent - 设备短时间重连事件: deviceId={}, source={}, clientIp={}",
                event.getDeviceId(), event.getReportSource(), event.getClientIp());

        // 推送设备上线事件(Mq)
        pushDeviceOnline(event);
        // 重连时提交到缓冲池异步更新
        updateLoginTimeAsync(event);
    }

    /**
     * 处理设备离线事件（定时任务检测）
     * @param event 设备离线事件
     */
    private void processDetectedDeviceOffline(DeviceOnlineStatusEvent event) {
        log.debug("DeviceStatusEvent - 标记设备离线事件: deviceId={}", event.getDeviceId());
        // 推送设备离线事件(Mq)
        pushDeviceOffline(event);
        // 推送在线时长
        pushDeviceOnlineTime(event);

    }

    /**
     * 处理设备状态缓存过期监听事件
     * @param event 设备状态缓存过期监听事件
     */
    private void processConfirmDeviceOffline(DeviceOnlineStatusEvent event) {
        log.debug("DeviceStatusEvent - 确认设备离线事件: deviceId={}", event.getDeviceId());
    }

    /**
     * 处理心跳事件
     * 设备持续在线时提交到异步缓冲池批量更新
     * @param event 心跳事件
     */
    private void processHeartbeat(DeviceOnlineStatusEvent event) {
        updateLoginTimeAsync(event);
    }

    // ==================== 登录时间更新辅助方法 ====================

    /**
     * 立即更新登录时间（用于首次上线）
     * 直接更新数据库，确保firstLoginTime不丢失
     */
    private void updateLoginTimeImmediate(DeviceOnlineStatusEvent event) {
        try {
            Long deviceId = event.getDeviceId();
            String clientIp = event.getClientIp();
            OffsetDateTime loginTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getEventTime()), ZoneOffset.UTC);

            // 立即更新到数据库
            deviceAccountRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime);

        } catch (Exception e) {
            log.error("DeviceLoginUpdate - 立即更新登录时间失败: deviceId={}", event.getDeviceId(), e);
        }
    }

    /**
     * 异步更新登录时间（用于重连和心跳）
     * 提交到缓冲池批量处理
     */
    private void updateLoginTimeAsync(DeviceOnlineStatusEvent event) {
        try {
            Long deviceId = event.getDeviceId();
            String clientIp = event.getClientIp();
            OffsetDateTime loginTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.getEventTime()), ZoneOffset.UTC);

            // 提交到异步缓冲池
            deviceLoginUpdatePort.submitLoginUpdate(deviceId, clientIp, loginTime);
        } catch (Exception e) {
            log.error("DeviceLoginUpdate - 提交登录时间异步更新失败: deviceId={}", event.getDeviceId(), e);
        }
    }

    // ==================== mq推送在线时长辅助方法 ====================

    /**
     * 推送设备在线时长记录
     * @param event 设备离线事件
     */
    private void pushDeviceOnlineTime(DeviceOnlineStatusEvent event) {

        if (event.getOnlineStartTime() == null || event.getLastReportTime() == null) {
            log.error("DeviceOnlineTime - 上线/离线时间为空，保存失败: deviceId={}, event={}", event.getDeviceId(), event);
            return;
        }

        // 时间合法性检查：防止时间顺序异常导致负数时长
        if (event.getOnlineStartTime() > event.getLastReportTime()) {
            log.error("DeviceOnlineTime - 时间顺序异常，跳过保存: deviceId={}, startTime={}, endTime={}",
                    event.getDeviceId(), event.getOnlineStartTime(), event.getLastReportTime());
            return;
        }

        // 最小时长校验：忽略无意义的极短连接记录（小于1秒）
        long durationMs = event.getLastReportTime() - event.getOnlineStartTime();
        if (durationMs < 1000) {
            log.debug("DeviceOnlineTime - 连接时长过短，跳过保存: deviceId={}, duration={}ms",
                    event.getDeviceId(), durationMs);
            return;
        }

        Map<String, Object> payload = Map.of(
                ONLINE_TIME, event.getOnlineStartTime(),
                OFFLINE_TIME, event.getLastReportTime()
        );

        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(event.getDeviceId())
                .eventType(ONLINE_TIME_EVENT_TYPE)
                .payload(payload)
                .occurredAt(resolveOccurredAt(event))
                .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.ONLINE_TIME, message);

    }

    // ==================== mq设备在线状态推送辅助方法 ====================

    private void pushDeviceOnline(DeviceOnlineStatusEvent event) {
        Map<String, Object> payload = new HashMap<>();
        if (event.getReportSource() != null) {
            payload.put(REPORT_SOURCE, event.getReportSource().name());
        }
        putIfNotNull(payload, CLIENT_IP, event.getClientIp());
        if (event.getEventType() != null) {
            payload.put(STATUS_EVENT_TYPE, event.getEventType().name());
        }

        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(event.getDeviceId())
                .eventType("status.online")
                .payload(payload)
                .occurredAt(resolveOccurredAt(event))
                .build();

        deviceEventPublisherPort.publishStatus(ONLINE, message);

    }

    private void pushDeviceOffline(DeviceOnlineStatusEvent event) {
        Map<String, Object> payload = new HashMap<>();
        putIfNotNull(payload, ONLINE_START_TIME, event.getOnlineStartTime());
        putIfNotNull(payload, LAST_REPORT_TIME, event.getLastReportTime());
        putIfNotNull(payload, CLIENT_IP, event.getClientIp());
        if (event.getEventType() != null) {
            payload.put(STATUS_EVENT_TYPE, event.getEventType().name());
        }

        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(event.getDeviceId())
                .eventType("status.offline")
                .payload(payload)
                .occurredAt(resolveOccurredAt(event))
                .build();

        deviceEventPublisherPort.publishStatus(OFFLINE, message);
    }

    private static void putIfNotNull(Map<String, Object> out, String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        out.put(key, value);
    }

    private static Instant resolveOccurredAt(DeviceOnlineStatusEvent event) {
        if (event == null || event.getEventTime() == null) {
            return Instant.now();
        }
        return Instant.ofEpochMilli(event.getEventTime());
    }
}
