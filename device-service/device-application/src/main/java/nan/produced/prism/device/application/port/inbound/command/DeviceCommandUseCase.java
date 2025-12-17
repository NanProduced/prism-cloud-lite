package nan.produced.prism.device.application.port.inbound.command;

import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.dto.command.DeviceCommandResultDTO;

import java.util.List;

/**
 * 设备指令用例
 *
 * @author Nan
 */
public interface DeviceCommandUseCase {

    /**
     * 分发指令
     *
     * @param commands 指令列表
     * @return 指令结果列表
     */
    List<DeviceCommandResultDTO> dispatch(List<DeviceCommand> commands);

    /**
     * 获取设备待执行的指令
     * @param deviceId 设备ID
     * @return 待执行的指令列表
     */
    List<DeviceCommand> getPendingCommands(Long deviceId);

    /**
     * 确认指令(注意确认只是说明设备接收到指令，并不代表执行状态)
     * @param deviceId 设备ID
     * @param queueId 指令ID
     * @param result 结果
     */
    void confirmCommand(Long deviceId, Integer queueId, String result);


}

