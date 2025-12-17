package nan.produced.prism.device.application.port.outbound.command;

import nan.produced.prism.device.application.domain.command.DeviceCommand;

/**
 * 指令WebSocket下发端口接口
 *
 * @author Nan
 */
public interface DeviceCommandWsPort {

    /**
     * 通过WebSocket发送指令到设备
     *
     * @param command 指令对象
     * @return 是否发送成功
     */
    boolean sendCommandViaWebsocket(DeviceCommand command);

    /**
     * 检查设备是否在线 (WebSocket连接存在)
     *
     * @param deviceId 设备ID
     * @return 是否在线
     */
    boolean isDeviceOnline(Long deviceId);
}
