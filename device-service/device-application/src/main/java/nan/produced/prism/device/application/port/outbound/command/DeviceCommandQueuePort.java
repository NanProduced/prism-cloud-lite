package nan.produced.prism.device.application.port.outbound.command;

import nan.produced.prism.device.application.domain.command.DeviceCommand;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

/**
 * 指令队列出站端口（device-service 内部缓存）
 */
public interface DeviceCommandQueuePort {

    /**
     * 保存指令到缓存
     * 实现去重逻辑：相同authorUrl的指令会被覆盖
     *
     * @param command 指令对象
     * @return 是否保存成功;是否覆盖
     */
    Pair<Boolean, Boolean> cacheCommand(DeviceCommand command);

    /**
     * 获取设备的所有待执行指令
     *
     * @param deviceId 设备ID
     * @return 指令列表
     */
    List<DeviceCommand> getPendingCommands(Long deviceId);

    /**
     * 根据指令ID获取指令详情
     *
     * @param deviceId 设备ID
     * @param queueId 指令ID
     * @return 指令对象
     */
    Optional<DeviceCommand> getCommand(Long deviceId, Integer queueId);

    /**
     * 删除指令 (确认执行后)
     *
     * @param deviceId 设备ID
     * @param queueId 指令ID
     * @return 删除的指令对象
     */
    DeviceCommand removeCommand(Long deviceId, Integer queueId);

    /**
     * 从指令队列里移除指令
     *
     * @param deviceId 设备ID
     * @param queueId 指令ID
     */
    void removeFromQueue(Long deviceId, Integer queueId);

}
