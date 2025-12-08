package nan.produced.prism.device.application.port.outbound.config;


import nan.produced.prism.device.application.properties.DomainDeviceProps;

public interface DeviceConfigPort {

    /**
     * 获取设备配置
     * @return 设备配置属性
     */
    DomainDeviceProps getDeviceConfig();

    /**
     * 是否启用Redis Stream查询
     * @return 是否启用
     */
    default boolean isStreamQueryEnabled() {
        return getDeviceConfig().getOnlineStatus().getStreamQuery().isEnabled();
    }

    /**
     * 获取缓冲池窗口时间(毫秒)
     * @return 窗口时间
     */
    default long getBufferPoolWindowMs() {
        return getDeviceConfig().getOnlineStatus().getBufferPool().getWindowMs();
    }

    /**
     * 获取Redis设备重连窗口TTL(秒)
     * @return TTL
     */
    default long getReconnectCacheTtl() {
        return getDeviceConfig().getOnlineStatus().getReconnectCacheTtl();
    }

    /**
     * 获取缓冲池刷盘任务延迟(毫秒)
     * @return 缓冲池任务延迟
     */
    default long getBufferPoolFlushTaskDelays() {
        return getDeviceConfig().getOnlineStatus().getBufferPool().getFlushTaskDelayMs();
    }

    /**
     * 获取设备离线超时阈值(毫秒)
     * @return 设备离线超时阈值
     */
    default long getDeviceOfflineThreshold() {
        return getDeviceConfig().getOnlineStatus().getOfflineThreshold();
    }

    /**
     * 获取设备离线检查间隔(毫秒)
     * @return 设备离线检查间隔
     */
    default long getDeviceOfflineCheckInterval() {
        return getDeviceConfig().getOnlineStatus().getOfflineCheckInterval();
    }

    /**
     * 获取设备离线检测任务初始延迟(毫秒)
     * @return 设备离线检测任务初始延迟
     */
    default long getDeviceOfflineInitialDelay() {
        return getDeviceConfig().getOnlineStatus().getOfflineCheckInitialDelay();
    }

    /**
     * 获取设备校准任务间隔(毫秒)
     * @return 设备校准任务间隔
     */
    default long getDeviceCalibrationInterval() {
        return getDeviceConfig().getOnlineStatus().getCalibrationInterval();
    }

}
