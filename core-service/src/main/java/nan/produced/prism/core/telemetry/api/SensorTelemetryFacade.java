package nan.produced.prism.core.telemetry.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.telemetry.api.dto.sensor.ReceiveCardSampleItem;
import nan.produced.prism.core.telemetry.api.dto.sensor.SensorMetricSeriesResponse;

public interface SensorTelemetryFacade {

    /**
     * 记录设备传感器上报（上报体为 JSON 数组，包含多种类型数据）。查询统一使用 occurredAt/serverTime。
     */
    void recordSensorReports(UUID userId, Long deviceId, String sensorData, Instant occurredAt, String traceId);

    SensorMetricSeriesResponse queryMetricSeries(
        UUID userId,
        Long deviceId,
        Instant from,
        Instant to,
        String sourceType,
        List<String> reportTypes,
        List<String> metricKeys,
        Integer limit);

    List<ReceiveCardSampleItem> listReceiveCardSamples(
        UUID userId,
        Long deviceId,
        Instant from,
        Instant to,
        Integer netPortNum,
        Integer receiveCardNum,
        Integer limit);
}
