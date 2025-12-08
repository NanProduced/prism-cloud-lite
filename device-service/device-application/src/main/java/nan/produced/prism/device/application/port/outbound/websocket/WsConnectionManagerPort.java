package nan.produced.prism.device.application.port.outbound.websocket;

import nan.produced.prism.device.application.domain.websocket.DeviceWsConnection;

import java.util.Collection;
import java.util.Optional;

/**
 * 连接管理器接口
 *
 * <p>定义WebSocket连接管理的核心抽象</p>
 *
 * @author Nan
 */
public interface WsConnectionManagerPort {

    /**
     * 添加WebSocket连接
     *
     * @param deviceId 设备ID
     * @param connection WebSocket连接
     * @return 是否添加成功
     */
    boolean addConnection(Long deviceId, DeviceWsConnection connection);

    /**
     * 移除WebSocket连接
     *
     * @param deviceId 设备ID
     * @return 被移除的连接，如果不存在返回null
     */
    DeviceWsConnection removeConnection(Long deviceId);

    /**
     * 获取WebSocket连接
     *
     * @param deviceId 设备ID
     * @return WebSocket连接的Optional包装
     */
    Optional<DeviceWsConnection> getConnection(Long deviceId);

    /**
     * 获取当前连接总数
     *
     * @return 连接数量
     */
    int getConnectionCount();

    /**
     * 获取所有在线设备ID
     *
     * @return 设备ID集合
     */
    Collection<Long> getOnlineDeviceIds();
}
