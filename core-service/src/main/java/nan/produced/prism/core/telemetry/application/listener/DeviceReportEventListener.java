package nan.produced.prism.core.telemetry.application.listener;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.api.event.DeviceMediaPlayRecordsReportedEvent;
import nan.produced.prism.core.device.api.event.DeviceOnlineSessionReportedEvent;
import nan.produced.prism.core.device.api.event.DeviceProgramPlayRecordsReportedEvent;
import nan.produced.prism.core.device.api.event.DeviceSensorDataReportedEvent;
import nan.produced.prism.core.telemetry.api.DeviceOnlineTimeFacade;
import nan.produced.prism.core.telemetry.api.PlaybackTelemetryFacade;
import nan.produced.prism.core.telemetry.api.SensorTelemetryFacade;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceReportEventListener {

    private final DeviceOnlineTimeFacade deviceOnlineTimeFacade;
    private final PlaybackTelemetryFacade playbackTelemetryFacade;
    private final SensorTelemetryFacade sensorTelemetryFacade;

    @EventListener
    public void on(DeviceMediaPlayRecordsReportedEvent event) {
        if (event == null) {
            return;
        }
        playbackTelemetryFacade.recordMediaPlayRecords(event.userId(), event.deviceId(), event.reports(), event.traceId());
    }

    @EventListener
    public void on(DeviceProgramPlayRecordsReportedEvent event) {
        if (event == null) {
            return;
        }
        playbackTelemetryFacade.recordProgramPlayRecords(event.userId(), event.deviceId(), event.reports(), event.traceId());
    }

    @EventListener
    public void on(DeviceSensorDataReportedEvent event) {
        if (event == null) {
            return;
        }
        sensorTelemetryFacade.recordSensorReports(
                event.userId(),
                event.deviceId(),
                event.sensorData(),
                event.occurredAt(),
                event.traceId());
    }

    @EventListener
    public void on(DeviceOnlineSessionReportedEvent event) {
        if (event == null) {
            return;
        }
        deviceOnlineTimeFacade.recordOnlineSession(
                event.userId(),
                event.deviceId(),
                event.onlineAt(),
                event.offlineAt(),
                event.traceId());
    }
}

