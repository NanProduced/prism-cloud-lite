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

    /**
     * 设备截图上报（截图已上传到 S3 后，将元数据通过 MQ 推送给 core-service）
     *
     * @param deviceId     设备ID
     * @param s3Key        S3 对象 Key（不包含 bucket/域名）
     * @param sizeBytes    文件大小（bytes）
     * @param contentType  文件 MIME 类型
     */
    void asyncPushScreenshotReport(Long deviceId, String s3Key, long sizeBytes, String contentType);
}
