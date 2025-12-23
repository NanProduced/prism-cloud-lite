package nan.produced.prism.core.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.messaging.DeviceEventMessage;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import nan.produced.prism.core.common.api.ProgramDownloadProgressUseCase;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.application.port.inbound.DeviceEventUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandFeedBackPort;
import nan.produced.prism.core.device.application.port.outbound.DevicePropertiesPort;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceProperties;
import nan.produced.prism.core.telemetry.api.DeviceOnlineTimeFacade;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * 设备事件应用层服务
 * 处理来自 device-service 的各类设备事件
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEventApplicationService implements DeviceEventUseCase {

    private final DevicePropertiesPort devicePropertiesPort;

    private final DeviceCommandFeedBackPort deviceCommandFeedBackPort;

    private final DeviceRepository deviceRepository;

    private final DeviceScreenshotApplicationService deviceScreenshotApplicationService;

    private final ProgramDownloadProgressUseCase programDownloadProgressApplicationService;

    private final DeviceOnlineTimeFacade deviceOnlineTimeFacade;

    private final RabbitMessagePublisher rabbitMessagePublisher;

    /**
     * 处理设备上线状态
     *
     * @param deviceId 设备ID
     * @param isOnline 是否在线
     * @param traceId 追踪ID
     * @param timestamp 时间戳
     */
    @Override
    public void handleDeviceOnlineStatus(Long deviceId, boolean isOnline, String traceId, Instant timestamp) {
        log.debug("处理设备上线状态: deviceId={}, isOnline={}, traceId={}",
                deviceId, isOnline, traceId);
        if (isOnline)  {
            deviceRepository.updateStatusWithOnboarding(deviceId, 1, LocalDateTime.ofInstant(timestamp, ZoneId.of("UTC")));
        }
        else {
            deviceRepository.updateStatus(deviceId, 0, LocalDateTime.ofInstant(timestamp, ZoneId.of("UTC")));
        }

        UUID userId = deviceRepository.findUserIdByDeviceId(deviceId);
        if (userId == null) {
            log.warn("设备上线状态推送 - 未找到设备所属用户，跳过SSE通知: deviceId={}, traceId={}", deviceId, traceId);
            return;
        }

        FrontendEventMessage message = FrontendEventMessage.builder()
                .success(true)
                .type("device.status.changed")
                .scope(FrontendEventMessage.Scope.builder()
                        .userId(userId)
                        .deviceId(deviceId)
                        .build())
                .data(Map.of(
                        "online", isOnline
                ))
                .occurredAt(timestamp)
                .traceId(traceId)
                .build();

        rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_DEVICE_STATUS_CHANGED, message);
    }

    /**
     * 处理设备指令执行结果
     *
     * @param message 设备事件消息
     */
    @Override
    public void handleCommandResult(DeviceEventMessage message) {

        switch (message.getEventType()) {
            case MessagingConstants.CommandTypes.CONFIRM -> deviceCommandFeedBackPort.handleCommandConfirm(
                    (String) message.getPayload().get("commandId"),
                    message.getDeviceId(),
                    (Integer) message.getPayload().get("queueId")
            );
            case MessagingConstants.CommandTypes.EXPIRED -> deviceCommandFeedBackPort.handleCommandExpired(
                    message.getDeviceId(),
                    (Integer) message.getPayload().get("queueId")
            );
            default -> log.warn("不支持的指令结果: eventType={}, traceId={}", message.getEventType(), message.getTraceId());
        }
    }

    /**
     * 通用事件处理入口
     *
     * @param message 设备事件消息
     */
    @Override
    public void handleDeviceEvent(DeviceEventMessage message) {
        if (message == null || message.getDeviceId() == null) {
            return;
        }

        String eventType = message.getEventType();
        String traceId = message.getTraceId();

        try {
            // 根据事件类型分发处理
            if (eventType == null) {
                log.warn("DeviceEventApplicationService - 事件类型为空，traceId={}", traceId);
                return;
            }

            switch (eventType) {
                // 属性上报事件
                case MessagingConstants.DeviceEventTypes.REPORT_PROPERTIES:
                    handleDeviceProperties(message.getDeviceId(),
                            message.getReportData(), traceId);
                    break;

                // 素材播放记录
                case MessagingConstants.DeviceEventTypes.REPORT_MEDIA_PLAY_RECORD:
                    handleMediaPlayRecord(message.getDeviceId(),
                            message.getReportData(), traceId);
                    break;

                // 节目播放记录
                case MessagingConstants.DeviceEventTypes.REPORT_PROGRAM_PLAY_RECORD:
                    handleProgramPlayRecord(message.getDeviceId(),
                            message.getReportData(), traceId);
                    break;

                // 设备日志
                case MessagingConstants.DeviceEventTypes.REPORT_DEVICE_LOG:
                    handleDeviceLog(message.getDeviceId(),
                            message.getReportData(), traceId);
                    break;

                // 传感器数据
                case MessagingConstants.DeviceEventTypes.REPORT_SENSOR_DATA:
                    handleSensorReport(message.getDeviceId(),
                            message.getReportData(), traceId);
                    break;

                // 下载进度
                case MessagingConstants.DeviceEventTypes.REPORT_DOWNLOADING_PROGRESS:
                    handleDownloadProgress(message.getDeviceId(),
                            message.getReportData(), traceId, message.getOccurredAt());
                    break;

                // 设备截图
                case MessagingConstants.DeviceEventTypes.REPORT_SCREENSHOT:
                    handleScreenshotUploaded(message.getDeviceId(), message.getPayload(), traceId, message.getOccurredAt());
                    break;

                case MessagingConstants.DeviceEventTypes.REPORT_ONLINE_TIME:
                    handleOnlineTime(message.getDeviceId(), message.getPayload(), traceId);
                    break;

                default:
                    log.warn("不支持的事件类型: eventType={}, traceId={}", eventType, traceId);
            }
        } catch (Exception e) {
            log.error("处理设备事件异常: deviceId={}, eventType={}, traceId={}", message.getDeviceId(), eventType, traceId, e);
            throw new BizException(ErrorCode.DEVICE_REPORT_EVENT_HANDLE_FAILED, e);
        }
    }

    /**
     * 处理设备属性上报
     * 设备上报 led_status 等属性信息
     *
     * @param deviceId 设备ID
     * @param properties 设备属性 JSON 字符串
     * @param traceId 追踪ID
     */
    private void handleDeviceProperties(Long deviceId, String properties, String traceId) {
        log.debug("处理设备属性上报: deviceId={}, properties={}, traceId={}",
                deviceId, properties, traceId);
        DeviceProperties deviceProperties = JsonUtils.fromJson(properties, DeviceProperties.class);
        // 存储
        devicePropertiesPort.handleDeviceProperties(deviceId, deviceProperties, traceId);
        // 检查指令结果
        deviceCommandFeedBackPort.chackCommandResult(deviceId, deviceProperties);
    }

    /**
     * 处理素材播放记录上报
     *
     * @param deviceId 设备ID
     * @param reportData 播放记录 JSON 字符串
     * @param traceId 追踪ID
     */
    private void handleMediaPlayRecord(Long deviceId, String reportData, String traceId) {
        log.debug("处理素材播放记录: deviceId={}, traceId={}", deviceId, traceId);

        // TODO: 实现素材播放记录处理逻辑
        // 1. 解析播放记录数据
        // 2. 存储播放记录
        // 3. 更新素材统计信息（播放次数、播放时长等）
    }

    /**
     * 处理节目播放记录上报
     *
     * @param deviceId 设备ID
     * @param reportData 播放记录 JSON 字符串
     * @param traceId 追踪ID
     */
    private void handleProgramPlayRecord(Long deviceId, String reportData, String traceId) {
        log.debug("处理节目播放记录: deviceId={}, traceId={}", deviceId, traceId);

        // TODO: 实现节目播放记录处理逻辑
        // 1. 解析播放记录数据
        // 2. 存储播放记录
        // 3. 更新节目统计信息
    }

    /**
     * 处理设备日志上报
     *
     * @param deviceId 设备ID
     * @param logs 日志 JSON 字符串
     * @param traceId 追踪ID
     */
    private void handleDeviceLog(Long deviceId, String logs, String traceId) {
        log.debug("处理设备日志上报: deviceId={}, traceId={}", deviceId, traceId);

        // TODO: 实现设备日志处理逻辑
        // 1. 解析日志数据
        // 2. 存储日志到日志系统（或对象存储）
        // 3. 分析日志提取错误信息
        // 4. 触发告警（如果有严重错误）
    }

    /**
     * 处理传感器数据上报
     *
     * @param deviceId 设备ID
     * @param sensorData 传感器数据 JSON 字符串
     * @param traceId 追踪ID
     */
    private void handleSensorReport(Long deviceId, String sensorData, String traceId) {
        log.debug("处理传感器数据: deviceId={}, traceId={}", deviceId, traceId);

        // TODO: 实现传感器数据处理逻辑
        // 1. 解析传感器数据（温度、湿度、等等）
        // 2. 存储时间序列数据
        // 3. 检查异常值并告警
        // 4. 更新设备状态
    }

    /**
     * 处理下载进度上报
     *
     * @param deviceId 设备ID
     * @param progress 下载进度数据 JSON 字符串
     * @param traceId 追踪ID
     * @param occurredAt 上报时间
     **/
    private void handleDownloadProgress(Long deviceId, String progress, String traceId, Instant occurredAt) {
        if (deviceId == null || progress == null || progress.isBlank()) {
            return;
        }

        log.debug("处理下载进度: deviceId={}, traceId={}", deviceId, traceId);
        UUID userId = deviceRepository.findUserIdByDeviceId(deviceId);
        programDownloadProgressApplicationService.handleDownloadingProgress(deviceId, userId, progress, occurredAt, traceId);
    }

    private void handleScreenshotUploaded(Long deviceId, Map<String, Object> payload, String traceId, Instant occurredAt) {
        String s3Key = getString(payload, "s3Key");
        long sizeBytes = getLong(payload, "sizeBytes");
        String contentType = getString(payload, "contentType");

        deviceScreenshotApplicationService.recordScreenshotUploaded(
                deviceId,
                s3Key,
                sizeBytes,
                contentType,
                occurredAt,
                traceId);
    }

    /**
     * 处理设备在线时长数据上报
     * @param deviceId 设备ID
     * @param payload  数据
     * @param traceId 追踪ID
     */
    private void handleOnlineTime(Long deviceId, Map<String, Object> payload, String traceId) {
        if (deviceId == null || payload == null) {
            return;
        }

        long onlineTime = getLong(payload, "onlineTime");
        long offlineTime = getLong(payload, "offlineTime");

        UUID userId = deviceRepository.findUserIdByDeviceId(deviceId);
        if (userId == null) {
            log.warn("OnlineTime - 未找到设备所属用户，跳过落库: deviceId={}, traceId={}", deviceId, traceId);
            return;
        }

        Instant onlineAt = Instant.ofEpochMilli(onlineTime);
        Instant offlineAt = Instant.ofEpochMilli(offlineTime);
        deviceOnlineTimeFacade.recordOnlineSession(userId, deviceId, onlineAt, offlineAt, traceId);
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return null;
        }
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private long getLong(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return 0L;
        }
        Object value = payload.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

}
