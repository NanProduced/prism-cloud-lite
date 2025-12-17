package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.CommonConstant;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.dto.command.DeviceCommandResultDTO;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.inbound.command.DeviceCommandUseCase;
import nan.produced.prism.device.application.port.outbound.command.DeviceCommandQueuePort;
import nan.produced.prism.device.application.port.outbound.command.DeviceCommandWsPort;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessException;
import nan.produced.prism.device.common.utils.CommandUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 指令投递应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandApplicationService implements DeviceCommandUseCase {

    private final DeviceCommandQueuePort deviceCommandQueuePort;

    private final DeviceCommandWsPort deviceCommandWsPort;

    private final DeviceEventPublisherPort deviceEventPublisherPort;

    @Override
    public List<DeviceCommandResultDTO> dispatch(List<DeviceCommand> commands) {
        if (CollectionUtils.isEmpty(commands)) {
            throw new BusinessException(BusinessErrorCode.COMMAND_CANNOT_BE_NULL);
        }
        return commands.stream()
                .map(this::sendCommandToDevice)
                .toList();
    }

    @Override
    public List<DeviceCommand> getPendingCommands(Long deviceId) {
        try {
            return deviceCommandQueuePort.getPendingCommands(deviceId);
        } catch (Exception e) {
            log.error("DeviceCommandQueue - 获取待处理指令异常, deviceId: {}", deviceId, e);
            return List.of();
        }
    }

    @Override
    public void confirmCommand(Long deviceId, Integer queueId, String result) {
        DeviceCommand command = deviceCommandQueuePort.removeCommand(deviceId, queueId);
        if (command != null) {
            deviceEventPublisherPort.publishCommand(CommonConstant.Command.CONFIRM, buildDeviceConfirmEvent(command.getCommandId(), command.getDeviceId()));
        }
    }

    /**
     * 发送指令
     * @param command 指令
     * @return 指令结果
     */
    private DeviceCommandResultDTO sendCommandToDevice(DeviceCommand command) {
        // 生成queueId
        command.setQueueId(CommandUtils.generateQueueId());
        // 缓存指令
        Pair<Boolean, Boolean> cacheResult = deviceCommandQueuePort.cacheCommand(command);
        if (!cacheResult.getLeft().equals(Boolean.TRUE)) {
            return DeviceCommandResultDTO.failed(command.getDeviceId(), command.getCommandId(), "指令缓存失败");
        }

        // 检查设备是否在线，在线则Websocket实时下发
        if (deviceCommandWsPort.isDeviceOnline(command.getDeviceId())) {
            boolean sent = deviceCommandWsPort.sendCommandViaWebsocket(command);
            if (sent) {
                return DeviceCommandResultDTO.success(command.getDeviceId(),
                        command.getCommandId(),
                        "Websocket",
                        command.getQueueId(),
                        cacheResult.getRight());
            }
            else {
                return DeviceCommandResultDTO.failed(command.getDeviceId(),
                        command.getCommandId(),
                        "Websocket指令下发失败");
            }
        }
        // 设备离线，等待HTTP轮询
        else {
            log.debug("DeviceCommandQueue - 设备离线，等待HTTP轮询, deviceId: {}", command.getDeviceId());
            return DeviceCommandResultDTO.success(command.getDeviceId(),
                    command.getCommandId(),
                    "Cache",
                    command.getQueueId(),
                    cacheResult.getRight());
        }
    }

    private DeviceEventMessage buildDeviceConfirmEvent(String commandId, Long deviceId) {
        Map<String, Object> payload = Map.of(CommonConstant.Command.COMMAND_ID, commandId);
        return DeviceEventMessage.builder()
                .deviceId(deviceId)
                .eventType(CommonConstant.Command.COMMAND_FEEDBACK)
                .payload(payload)
                .retryable(true)
                .build();
    }
}
