package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;

public interface DeviceCommandLogRepository {

    void save(DeviceCommandLog  log);

    void saveAll(Iterable<DeviceCommandLog> logs);


    /**
     * 根据指令Id更新指令执行状态
     * @param commandId 指令Id
     * @param status CONFIRMED|COMPLETED|EXPIRED
     */
    void updateStatus(String commandId, DeviceCommandStatus status);

    /**
     * 根据指令Id查询指令
     * @param operationId 操作id(commandId)
     * @return 指令日志
     */
    DeviceCommandLog findByOperationId(String operationId);

    /**
     * 根据设备Id和指令队列Id查询指令(有些情况device-service无法上报commandId)
     * @param deviceId 设备Id
     * @param queueId 指令队列Id
     * @return
     */
    DeviceCommandLog findByDeviceIdAndQueueId(Long deviceId, Integer queueId);


}
