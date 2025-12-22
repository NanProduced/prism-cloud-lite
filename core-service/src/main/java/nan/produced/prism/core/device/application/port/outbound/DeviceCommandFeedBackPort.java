package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.DeviceProperties;

public interface DeviceCommandFeedBackPort {

    /**
     * 处理指令确认
     * @param commandId 指令ID
     * @param deviceId 设备ID
     * @param queueId 队列ID
     */
    void handleCommandConfirm(String commandId, Long deviceId, Integer queueId);

    /**
     * 检查指令结果
     * @param deviceId 设备ID
     * @param properties 设备属性
     */
    void chackCommandResult(Long deviceId, DeviceProperties properties);

    /**
     * 处理指令过期
     * @param deviceId 设备ID
     * @param queueId 队列ID
     */
    void handleCommandExpired(Long deviceId, Integer queueId);

}
