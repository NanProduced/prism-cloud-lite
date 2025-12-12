package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.domain.CommonConstant;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.inbound.status.DeviceReportUseCase;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
                        .eventType(CommonConstant.Report.PROPERTIES)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.PROPERTIES, message);

    }

    @Override
    public void asyncPushMediaPlayRecordReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(CommonConstant.Report.MEDIA_PLAY_RECORD)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.MEDIA_PLAY_RECORD, message);


    }

    @Override
    public void asyncPushProgramPlayRecordReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(CommonConstant.Report.PROGRAM_PLAY_RECORD)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.PROGRAM_PLAY_RECORD, message);

    }

    @Override
    public void asyncPushDeviceLog(Long deviceId, String logs) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(CommonConstant.Report.DEVICE_LOG)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.DEVICE_LOG, message);

    }

    @Override
    public void asyncHandleSensorReport(Long deviceId, String reports) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(CommonConstant.Report.SENSOR_DATA)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.SENSOR_DATA, message);

    }

    @Override
    public void asyncSaveDownloadingReport(Long deviceId, String reportStr) {

        DeviceEventMessage message = DeviceEventMessage.builder()
                        .deviceId(deviceId)
                        .eventType(CommonConstant.Report.DOWNLOADING_PROGRESS)
                        .occurredAt(Instant.now())
                        .build();

        deviceEventPublisherPort.publishReport(CommonConstant.Report.DOWNLOADING_PROGRESS, message);

    }
}
