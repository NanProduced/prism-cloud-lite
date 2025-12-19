package nan.produced.prism.device.infrastructure.event;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.event.DeviceOnlineStatusEvent;
import nan.produced.prism.device.application.dto.record.DeviceOnlineTimeRecord;
import nan.produced.prism.device.application.dto.record.DeviceReconnectRecord;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.application.port.outbound.repository.DeviceOnlineTimeRecordRepository;
import nan.produced.prism.device.application.port.outbound.repository.DeviceReconnectRecordRepository;
import nan.produced.prism.device.application.port.outbound.status.DeviceLoginUpdatePort;
import nan.produced.prism.device.common.utils.TimeUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
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

    private final DeviceOnlineTimeRecordRepository deviceOnlineTimeRecordRepository;

    private final DeviceReconnectRecordRepository deviceReconnectRecordRepository;

    private final DeviceLoginUpdatePort deviceLoginUpdatePort;

    private final DeviceEventPublisherPort deviceEventPublisherPort;

    @Async
    @EventListener
    public void handleDeviceOnlineStatusEvent(DeviceOnlineStatusEvent event) {
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
        // 记录重连信息
        saveTerminalReconnect(event);
    }

    /**
     * 处理设备离线事件（定时任务检测）
     * @param event 设备离线事件
     */
    private void processDetectedDeviceOffline(DeviceOnlineStatusEvent event) {
        log.debug("DeviceStatusEvent - 标记设备离线事件: deviceId={}", event.getDeviceId());
        // 推送设备离线事件(Mq)
        pushDeviceOffline(event);
        // 记录在线时长
        saveTerminalOnlineTime(event);

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
            LocalDateTime loginTime = TimeUtils.convertTimestampToLocalDateTime(event.getEventTime());

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
            LocalDateTime loginTime = TimeUtils.convertTimestampToLocalDateTime(event.getEventTime());

            // 提交到异步缓冲池
            deviceLoginUpdatePort.submitLoginUpdate(deviceId, clientIp, loginTime);
        } catch (Exception e) {
            log.error("DeviceLoginUpdate - 提交登录时间异步更新失败: deviceId={}", event.getDeviceId(), e);
        }
    }



    // ==================== 终端异常重连记录辅助方法 ====================

    /**
     * 保存设备重连信息
     * @param event 重连事件
     */
    private void saveTerminalReconnect(DeviceOnlineStatusEvent event) {

        DeviceReconnectRecord reconnectRecord = DeviceReconnectRecord.builder()
                .deviceId(event.getDeviceId())
                .startOnlineTime(TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime()))
                .lastReportTime(TimeUtils.convertTimestampToLocalDateTime(event.getLastReportTime()))
                .reconnectTime(TimeUtils.convertTimestampToLocalDateTime(event.getEventTime()))
                .reconnectIp(event.getClientIp())
                .reconnectSource(event.getReportSource().name())
                .build();

        deviceReconnectRecordRepository.saveReconnectRecord(reconnectRecord);
        log.debug("DeviceReconnect - 设备重连信息保存成功: deviceId={}, info={}", event.getDeviceId(), event);
    }

    // ==================== 记录在线时长辅助方法 ====================

    /**
     * 保存设备在线时长记录
     * @param event 设备离线事件
     */
    private void saveTerminalOnlineTime(DeviceOnlineStatusEvent event) {

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

        DeviceOnlineTimeRecord onlineTimeRecord = DeviceOnlineTimeRecord.builder()
                .deviceId(event.getDeviceId())
                .startTime(TimeUtils.convertTimestampToLocalDateTime(event.getOnlineStartTime()))
                .endTime(TimeUtils.convertTimestampToLocalDateTime(event.getLastReportTime()))
                .build();

        deviceOnlineTimeRecordRepository.saveDeviceOnlineTimeRecord(onlineTimeRecord);
        long durationSeconds = durationMs / 1000;

        log.debug("DeviceOnlineTime - 设备在线时长记录保存成功: deviceId={}, duration={}s",
                event.getDeviceId(), durationSeconds);
    }

    // ==================== mq设备在线状态推送辅助方法 ====================

    private void pushDeviceOnline(DeviceOnlineStatusEvent event) {
        Map<String, Object> payload = Map.of(
                REPORT_SOURCE, event.getReportSource().name(),
                CLIENT_IP, event.getClientIp(),
                STATUS_EVENT_TYPE, event.getEventType().name()
        );
        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(event.getDeviceId())
                .eventType("status.online")
                .payload(payload)
                .occurredAt(Instant.ofEpochMilli(event.getEventTime()))
                .build();

        deviceEventPublisherPort.publishStatus(ONLINE, message);

    }

    private void pushDeviceOffline(DeviceOnlineStatusEvent event) {
        Map<String, Object> payload = Map.of(
                ONLINE_START_TIME, event.getOnlineStartTime(),
                LAST_REPORT_TIME, event.getLastReportTime(),
                CLIENT_IP, event.getClientIp(),
                STATUS_EVENT_TYPE, event.getEventType().name()
        );
        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(event.getDeviceId())
                .eventType("status.offline")
                .payload(payload)
                .occurredAt(Instant.ofEpochMilli(event.getEventTime()))
                .build();

        deviceEventPublisherPort.publishStatus(OFFLINE, message);
    }
}
