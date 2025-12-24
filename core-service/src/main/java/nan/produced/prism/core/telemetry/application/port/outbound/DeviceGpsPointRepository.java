package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceGpsPointRepository {

    record InsertRow(
        UUID userId,
        Long deviceId,
        double longitude,
        double latitude,
        Float accuracy,
        Float altitude,
        Float speed,
        Double direct,
        Integer satellites,
        String reportTimeRaw,
        OffsetDateTime serverTime,
        String extraJson
    ) {
    }

    record PointRow(
        Long deviceId,
        double longitude,
        double latitude,
        Float accuracy,
        Float altitude,
        Float speed,
        Double direct,
        Integer satellites,
        OffsetDateTime serverTime
    ) {
    }

    record HeatmapCellRow(double longitude, double latitude, long count) {
    }

    int insertBatch(List<InsertRow> rows);

    List<PointRow> listLatestPerDevice(UUID userId);

    List<PointRow> listTrack(UUID userId, Long deviceId, OffsetDateTime from, OffsetDateTime to, Integer limit);

    List<HeatmapCellRow> heatmap(UUID userId, OffsetDateTime from, OffsetDateTime to, int precision, Integer limit);
}
