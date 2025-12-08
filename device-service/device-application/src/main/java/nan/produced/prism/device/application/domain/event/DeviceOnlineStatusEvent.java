package nan.produced.prism.device.application.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.device.application.domain.status.ReportSource;

/**
 * 设备状态变更事件基类
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceOnlineStatusEvent {

    private Long deviceId;

    private Long eventTime;

    private EventType eventType;

    private ReportSource reportSource;

    private String clientIp;

    private Long onlineStartTime;

    private Long lastReportTime;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 设备首次上线（缓存不存在的首次上报）
         */
        DEVICE_GO_LIVE,

        /**
         * 设备重连（短时间离线后重新连接）
         */
        DEVICE_RECONNECT,

        /**
         * 设备持续在线心跳更新
         */
        DEVICE_HEARTBEAT,

        /**
         * 设备被检测为离线（定时任务检测）
         */
        DEVICE_DETECTED_OFFLINE,

        /**
         * 设备正式下线（TTL过期确认）
         */
        DEVICE_CONFIRMED_OFFLINE

    }

    /**
     * 创建设备上线事件
     * @param deviceId 设备ID
     * @param source 上报源
     * @param clientIp IP
     */
    public static DeviceOnlineStatusEvent createGoLiveEvent(Long deviceId, ReportSource source, String clientIp) {
        return DeviceOnlineStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_GO_LIVE)
                .reportSource(source)
                .clientIp(clientIp)
                .build();
    }

    /**
     * 设备重连
     * @param deviceId 设备ID
     * @param source 上报源
     * @param clientIp IP
     * @return 重连事件
     */
    public static DeviceOnlineStatusEvent createReconnectEvent(Long deviceId, ReportSource source, String clientIp, Long onlineStartTime, Long lastReportTime) {
        return DeviceOnlineStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_RECONNECT)
                .reportSource(source)
                .clientIp(clientIp)
                .onlineStartTime(onlineStartTime)
                .lastReportTime(lastReportTime)
                .build();
    }

    /**
     * 创建设备离线事件（定时任务标记）
     * @param deviceId 设备ID
     * @param onlineStartTime 上线开始时间戳(毫秒)
     * @param lastReportTime 最后上报时间戳(毫秒)
     * @return 离线事件
     */
    public static DeviceOnlineStatusEvent createDetectedOfflineEvent(Long deviceId, Long onlineStartTime, Long lastReportTime) {
        return DeviceOnlineStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_DETECTED_OFFLINE)
                .onlineStartTime(onlineStartTime)
                .lastReportTime(lastReportTime)
                .build();
    }

    /**
     * 创建确认设备离线事件（状态键过期）
     * @param deviceId 设备Id
     * @return 设备状态事件
     */
    public static DeviceOnlineStatusEvent createConfirmOfflineEvent(Long deviceId) {
        return DeviceOnlineStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_CONFIRMED_OFFLINE)
                .build();
    }

    /**
     * 创建刷新事件-心跳
     */
    public static DeviceOnlineStatusEvent createHeartbeatEvent(Long deviceId, ReportSource source, String clientIp) {
        return DeviceOnlineStatusEvent.builder()
                .deviceId(deviceId)
                .eventTime(System.currentTimeMillis())
                .eventType(EventType.DEVICE_HEARTBEAT)
                .reportSource(source)
                .clientIp(clientIp)
                .build();
    }
}
