package nan.produced.prism.core.device.application.port.inbound;


import nan.produced.prism.core.common.messaging.DeviceEventMessage;

import java.time.Instant;

/**
 * 设备事件处理 UseCase
 * 处理来自 device-service 的设备上报事件
 *
 * @author Nan
 */
public interface DeviceEventUseCase {

    /**
     * 处理设备上线状态
     *
     * @param deviceId 设备ID
     * @param isOnline 是否在线
     * @param traceId 追踪ID
     * @param timestamp 时间戳
     */
    void handleDeviceOnlineStatus(Long deviceId, boolean isOnline, String traceId, Instant timestamp);

    /**
     * 处理设备指令执行结果
     *
     * @param deviceId 设备ID
     * @param commandResult 指令执行结果
     * @param traceId 追踪ID
     */
    void handleCommandResult(Long deviceId, String commandResult, String traceId);

    /**
     * 处理设备事件消息
     *
     * @param message 设备事件消息
     */
    void handleDeviceEvent(DeviceEventMessage message);
}