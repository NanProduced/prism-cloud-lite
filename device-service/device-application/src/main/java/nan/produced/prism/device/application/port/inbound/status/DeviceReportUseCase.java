package nan.produced.prism.device.application.port.inbound.status;

/**
 * 设备上报数据处理服务
 *
 * @author Nan
 */
public interface DeviceReportUseCase {

    /**
     * 异步处理终端信息上报
     * @param deviceId 设备Id
     * @param properties 上报Json
     */
    void asyncPushDeviceProperties(Long deviceId, String properties);

    /**
     * 异步处理素材播放记录上报
     * @param deviceId 设备Id
     * @param reportStr 上报Json
     */
    void asyncPushMediaPlayRecordReport(Long deviceId, String reportStr);

    /**
     * 异步处理节目播放记录上报
     * @param deviceId 设备Id
     * @param reportStr 上报Json
     */
    void asyncPushProgramPlayRecordReport(Long deviceId, String reportStr);

    /**
     * 异步处理终端日志上报
     * @param deviceId 设备Id
     * @param logs 日志Json
     */
    void asyncPushDeviceLog(Long deviceId, String logs);

    /**
     * 处理传感器上报
     * @param deviceId 设备Id
     * @param reports 传感器上报数据列表
     */
    void asyncPushSensorReport(Long deviceId, String reports);

    /**
     * 异步保存下载进度
     * @param deviceId 设备Id
     * @param reportStr 上报数据
     */
    void asyncPushDownloadingReport(Long deviceId, String reportStr);
}
