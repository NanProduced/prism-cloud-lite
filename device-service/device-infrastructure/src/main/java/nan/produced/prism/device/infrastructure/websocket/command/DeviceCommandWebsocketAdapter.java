package nan.produced.prism.device.infrastructure.websocket.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.domain.websocket.DeviceWsConnection;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.domain.websocket.WebsocketSession;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketMessage;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketMessageType;
import nan.produced.prism.device.application.port.outbound.command.DeviceCommandWsPort;
import nan.produced.prism.device.application.port.outbound.websocket.WsConnectionManagerPort;
import nan.produced.prism.device.common.utils.JsonUtils;
import nan.produced.prism.device.infrastructure.websocket.connection.DeviceWsSession;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 指令WebSocket下发适配器
 * 集成现有的连接管理器，实现指令实时下发
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandWebsocketAdapter implements DeviceCommandWsPort {

    private final WsConnectionManagerPort wsConnectionManagerPort;

    @Override
    public boolean sendCommandViaWebsocket(DeviceCommand command) {

        try {
            // 1. 获取设备连接
            Optional<DeviceWsConnection> connectionOpt = wsConnectionManagerPort.getConnection(command.getDeviceId());
            if (connectionOpt.isEmpty()) {
                log.warn("DeviceCommandWsPort - 设备未连接，无法下发指令, deviceId: {}", command.getDeviceId());
                return false;
            }

            DeviceWsConnection deviceWsConnection = connectionOpt.get();

            // 2. 检查连接状态
            if (!isConnectionValid(deviceWsConnection)) {
                log.warn("DeviceCommandWsPort - 设备连接无效，无法下发指令, deviceId: {}", command.getDeviceId());
                return false;
            }

            // 3. 构造WebSocket消息格式
            String message = buildWebsocketMessage(command, deviceWsConnection.getProtocolVersion());

            // 4. 发送消息
            Object session = deviceWsConnection.getSession();
            if (session instanceof DeviceWsSession wsSession) {
                boolean sent = wsSession.sendMessage(message);

                if (sent) {
                    log.debug("DeviceCommandWsPort - 指令已发送, deviceId: {}, message: {}", deviceWsConnection.getDeviceId(), message);
                    return true;
                }
                else {
                    log.warn("DeviceCommandWsPort - 指令下发失败, deviceId: {}", deviceWsConnection.getDeviceId());
                    return false;
                }
            }
            else {
                log.error("CommandWebSocketAdapter - 连接会话类型不匹配, 期望TerminalWebsocketSession, 实际: {}",
                        session != null ? session.getClass().getSimpleName() : "null");
                return false;
            }
        } catch (Exception e) {
            log.error("DeviceCommandWsPort - 命令下发异常, deviceId: {}", command.getDeviceId(), e);
            return false;
        }
    }

    @Override
    public boolean isDeviceOnline(Long deviceId) {
        try {
            Optional<DeviceWsConnection> connection = wsConnectionManagerPort.getConnection(deviceId);
            return connection.filter(this::isConnectionValid).isPresent();

        } catch (Exception e) {
            log.error("CommandWebSocketAdapter - 检查设备在线状态异常, deviceId: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 检查连接有效性
     * @param connection 连接
     * @return 是否有效
     */
    private boolean isConnectionValid(DeviceWsConnection connection) {
        if (connection == null || connection.getSession() == null) {
            return false;
        }

        Object session = connection.getSession();

        if (session instanceof DeviceWsSession wsSession) {
            return wsSession.isConnected();
        }

        return false;
    }

    /**
     * 构建WebSocket消息格式
     * 根据文档，WebSocket指令格式比HTTP稍有不同，包含额外的led_id字段
     *
     * @param command 指令对象，包含要发送给终端的指令信息
     * @param version 协议版本，决定消息的封装格式
     * @return 封装后的WebSocket消息字符串
     * @throws IllegalArgumentException 如果deviceId为null
     */
    private String buildWebsocketMessage(DeviceCommand command, ProtocolVersion version) {
        // 这里是设备要求一个Integer的设备Id，但实际这个Id无业务意义，使用Long转换适配设备要求的格式即可
        int deviceInt = command.getDeviceId().intValue();

        // 创建WebSocket内容对象
        WebsocketDeviceCommand.WebsocketContent content = new WebsocketDeviceCommand.WebsocketContent(command.getContentRaw());
        // 构造WebSocket指令数据
        WebsocketDeviceCommand.WebsocketCommand data = new WebsocketDeviceCommand.WebsocketCommand(command.getQueueId(),
                deviceInt,
                command.getAuthorUrl(),
                command.getKarma(),
                content);

        if (version == null || version == ProtocolVersion.V1_0) {
            return JsonUtils.toJson(new WebsocketDeviceCommand(List.of(data), deviceInt));
        }
        else if (version == ProtocolVersion.V1_1) {
            return JsonUtils.toJson(new V11WebsocketMessage(V11WebsocketMessageType.COMMAND.getId(), Collections.singletonList(data)));
        }
        else {
            return JsonUtils.toJson(new WebsocketDeviceCommand(List.of(data), deviceInt));
        }
    }
}
