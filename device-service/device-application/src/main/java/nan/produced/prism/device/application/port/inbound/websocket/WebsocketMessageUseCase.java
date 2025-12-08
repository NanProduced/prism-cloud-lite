package nan.produced.prism.device.application.port.inbound.websocket;

import nan.produced.prism.device.application.domain.websocket.DeviceWsConnection;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.domain.websocket.WebsocketSession;
import nan.produced.prism.device.application.domain.websocket.WsMessageProcessingContext;

import java.util.List;

/**
 * WebSocket消息处理用例接口
 *
 * @author Nan
 */
public interface WebsocketMessageUseCase {

    /**
     * 处理文本消息
     *
     * @param context 消息上下文
     */
    void handleTextMessageByProcessor(WsMessageProcessingContext context);

    /**
     * 处理连接建立
     *
     * @param deviceId 设备ID
     * @param session 技术会话对象
     * @param protocolVersion 协议版本
     * @return 终端连接对象
     */
    DeviceWsConnection handleConnectionEstablished(Long deviceId, WebsocketSession session, ProtocolVersion protocolVersion);

    /**
     * 处理连接断开
     *
     * @param deviceId 设备ID
     */
    void handleConnectionClosed(Long deviceId);

    /**
     * 处理PING帧
     * @param deviceWsConnection 设备连接
     */
    void handlePingFrame(DeviceWsConnection deviceWsConnection);

    /**
     * 处理PONG帧
     * @param deviceWsConnection 设备连接
     */
    void handlePongFrame(DeviceWsConnection deviceWsConnection);

    /**
     * 发送消息给指定设备
     *
     * @param deviceId 设备ID
     * @param message 消息内容
     * @return 是否发送成功
     */
    boolean sendMessage(Long deviceId, String message);

    /**
     * 批量发送消息给多个设备
     *
     * @param deviceIds 设备ID列表
     * @param message 消息内容
     * @return 发送成功的设备ID列表
     */
    List<Long> broadcastMessage(List<Long> deviceIds, String message);
}
