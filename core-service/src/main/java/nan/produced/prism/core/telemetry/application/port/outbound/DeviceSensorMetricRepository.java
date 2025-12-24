package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceSensorMetricRepository {

    record InsertRow(
        UUID userId,
        Long deviceId,
        String sourceType,
        String reportType,
        String sensorType,
        Integer sensorId,
        String metricKey,
        Double valueNum,
        String reportTimeRaw,
        OffsetDateTime serverTime
    ) {
    }

    record PointRow(
        String sourceType,
        String reportType,
        String metricKey,
        OffsetDateTime serverTime,
        Double valueNum
    ) {
    }

    int insertBatch(List<InsertRow> rows);

    List<PointRow> listPoints(
        UUID userId,
        Long deviceId,
        OffsetDateTime from,
        OffsetDateTime to,
        String sourceType,
        List<String> reportTypes,
        List<String> metricKeys,
        Integer limit);
}
