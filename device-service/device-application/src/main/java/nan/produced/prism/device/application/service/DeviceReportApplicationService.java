package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.domain.CommonConstant;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.inbound.status.DeviceReportUseCase;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import nan.produced.prism.device.application.messaging.DeviceEventRoutingKeys;

/**
 * 终端上报数据处理应用层服务
 *
 * @author Nan
 */
@Service
@RequiredArgsConstructor
public class DeviceReportApplicationService implements DeviceReportUseCase {

    private final DeviceEventPublisherPort deviceEventPublisherPort;

    /**
     * 异步处理终端信息上报 - 推送到device-events exchange
     * @param deviceId 设备Id
     * @param properties 上报Json
     */
    @Override
    @Async
    public void asyncPushDeviceProperties(Long deviceId, String properties) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.PROPERTIES))
                        .reportData( properties)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.PROPERTIES, message);

    }

    @Override
    public void asyncPushMediaPlayRecordReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.MEDIA_PLAY_RECORD))
                        .reportData(reportStr)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.MEDIA_PLAY_RECORD, message);


    }

    @Override
    public void asyncPushProgramPlayRecordReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.PROGRAM_PLAY_RECORD))
                        .reportData(reportStr)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.PROGRAM_PLAY_RECORD, message);

    }

    @Override
    public void asyncPushDeviceLog(Long deviceId, String logs) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.DEVICE_LOG))
                        .reportData(logs)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.DEVICE_LOG, message);

    }

    @Override
    public void asyncPushSensorReport(Long deviceId, String reports) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.SENSOR_DATA))
                        .reportData(reports)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.SENSOR_DATA, message);

    }

    @Override
    public void asyncPushDownloadingReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.DOWNLOADING_PROGRESS))
                        .reportData(reportStr)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.DOWNLOADING_PROGRESS, message);

    }

    /**
     * 异步处理截图上报
     * @param deviceId     设备ID
     * @param s3Key        S3 对象 Key（不包含 bucket/域名）
     * @param sizeBytes    文件大小（bytes）
     * @param contentType  文件 MIME 类型
     */
    @Override
    @Async
    public void asyncPushScreenshotReport(Long deviceId, String s3Key, long sizeBytes, String contentType) {
        if (deviceId == null || s3Key == null || s3Key.isBlank()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("s3Key", s3Key);
        payload.put("sizeBytes", sizeBytes);
        if (contentType != null && !contentType.isBlank()) {
            payload.put("contentType", contentType);
        }

        DeviceEventMessage message = DeviceEventMessage.builder()
                .deviceId(deviceId)
                .eventType(DeviceEventRoutingKeys.report(CommonConstant.Report.SCREENSHOT))
                .payload(payload)
                .occurredAt(Instant.now())
                .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.SCREENSHOT, message);
    }
}
