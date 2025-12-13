package nan.produced.prism.core.device.infrastructure.messaging.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.messaging.DeviceEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.device.application.port.inbound.DeviceEventUseCase;
import nan.produced.prism.core.device.infrastructure.messaging.idempotent.DeviceEventIdempotentHandler;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static nan.produced.prism.core.common.exception.ErrorCode.MQ_MESSAGE_CONSUMING_FAILED;

/**
 * 设备事件消息监听器
 * 监听来自 device-service 的设备上报数据（device.events 交换机）
 *
 * 监听的消息类型：
 * - status.* 设备在线/离线状态变化
 * - command.* 指令执行结果
 * - report.* 设备上报数据（属性、日志、传感器、播放记录等）
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventListener {

    public static final String STATUS_CATEGORY = "status";
    public static final String COMMAND_CATEGORY = "command";
    public static final String REPORT_CATEGORY = "report";

    private final DeviceEventUseCase deviceEventUseCase;
    private final DeviceEventIdempotentHandler idempotentHandler;

    /**
     * 监听设备在线状态队列
     * 处理 device.events exchange 中 status.* 的消息
     *
     * @param message 设备事件消息
     */
    @Async("deviceEventExecutor")
    @RabbitListener(queues = MessagingConstants.Queues.DEVICE_STATUS)
    public void handleDeviceStatusEvent(DeviceEventMessage message) {
        handleEvent(STATUS_CATEGORY, message, () -> {
            // 提取事件类型（status.online 或 status.offline）
            String eventType = message.getEventType();
            boolean isOnline = eventType != null && eventType.endsWith("online");

            deviceEventUseCase.handleDeviceOnlineStatus(
                    message.getDeviceId(),
                    isOnline,
                    message.getTraceId(),
                    message.getOccurredAt()
            );
        });
    }

    /**
     * 监听设备指令执行结果队列
     * 处理 device.events exchange 中 command.* 的消息
     *
     * @param message 设备事件消息
     */
    @Async("deviceEventExecutor")
    @RabbitListener(queues = MessagingConstants.Queues.DEVICE_COMMAND)
    public void handleDeviceCommandEvent(DeviceEventMessage message) {
        handleEvent(COMMAND_CATEGORY, message, () ->
                deviceEventUseCase.handleCommandResult(
                        message.getDeviceId(),
                        message.getPayload().toString(),
                        message.getTraceId()
                )
        );
    }

    /**
     * 监听设备数据上报队列
     * 处理 device.events exchange 中 report.* 的消息
     * 处理内容包括：
     * - report.properties 设备属性上报
     * - report.mediaPlayRecord 素材播放记录
     * - report.programPlayRecord 节目播放记录
     * - report.deviceLog 设备日志
     * - report.sensorData 传感器数据
     * - report.downloadingProgress 下载进度
     *
     * @param message 设备事件消息
     */
    @Async("deviceEventExecutor")
    @RabbitListener(queues = MessagingConstants.Queues.DEVICE_REPORT)
    public void handleDeviceReportEvent(DeviceEventMessage message) {
        handleEvent(REPORT_CATEGORY, message, () ->
                deviceEventUseCase.handleDeviceEvent(message)
        );
    }

    /**
     * 统一的事件处理框架
     * 负责：
     * 1. 幂等性检查（防重复）
     * 2. 业务处理
     * 3. 异常处理和日志
     *
     * @param eventCategory 事件分类（STATUS/COMMAND/REPORT）
     * @param message 消息体
     * @param processor 业务处理逻辑
     */
    private void handleEvent(String eventCategory, DeviceEventMessage message, Runnable processor) {
        if (message == null || message.getDeviceId() == null) {
            log.warn("DeviceEventListener -mq- 设备事件消息无效: eventCategory={}", eventCategory);
            return;
        }

        Long deviceId = message.getDeviceId();
        String traceId = message.getTraceId();
        String eventType = message.getEventType();

        try {
            // 1. 检查幂等性：防止重复处理同一条消息
            if (idempotentHandler.isDuplicate(eventCategory, deviceId, traceId)) {
                log.debug("DeviceEventListener -mq- 跳过重复消息处理: eventCategory={}, deviceId={}, traceId={}, eventType={}",
                        eventCategory, deviceId, traceId, eventType);
                return;
            }

            // 2. 执行业务逻辑
            processor.run();

        } catch (Exception e) {
            log.error("DeviceEventListener -mq- 设备事件处理异常: eventCategory={}, deviceId={}, traceId={}, eventType={}",
                    eventCategory, deviceId, traceId, eventType, e);

            if (isRetryableException(e) || message.isRetryable()) {
                idempotentHandler.removeIdempotentRecord(eventCategory, deviceId, traceId);
                log.warn("DeviceEventListener -mq- 删除幂等性记录允许重试: deviceId={}, traceId={}", deviceId, traceId);
            }



            // 重新抛出异常，让 RabbitMQ 处理（可配置重试）
            throw new InfraException(MQ_MESSAGE_CONSUMING_FAILED, "设备事件处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断异常是否可重试
     * 返回 true 表示该异常是可重试的（如网络超时、临时错误）
     * 返回 false 表示该异常不可重试（如业务异常）
     *
     * @param e 异常
     * @return 是否可重试
     */
    private boolean isRetryableException(Exception e) {
        // 判断异常类型是否是暂时性的
        // 例如：
        // - 数据库连接异常
        // - Redis 连接异常
        // - 网络超时
        // - HTTP 请求超时

        String exceptionName = e.getClass().getName();

        // 示例：判断是否是数据库相关异常
        if (exceptionName.contains("DataAccessException") ||
                exceptionName.contains("TimeoutException") ||
                exceptionName.contains("ConnectionException")) {
            return true;
        }

        // 其他异常暂时不重试
        return false;
    }
}
