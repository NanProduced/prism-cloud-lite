package nan.produced.prism.core.telemetry.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsDeviceLocationItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsHeatmapCellItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsPointItem;
import nan.produced.prism.core.telemetry.api.dto.gps.ManualLocationItem;

public interface GpsTelemetryFacade {

    List<GpsDeviceLocationItem> listLatestLocations(UUID userId);

    List<GpsPointItem> listTrack(UUID userId, Long deviceId, Instant from, Instant to, Integer limit);

    List<GpsHeatmapCellItem> heatmap(UUID userId, Instant from, Instant to, int precision, Integer limit);

    ManualLocationItem upsertManualLocation(UUID userId, Long deviceId, double longitude, double latitude);

    void deleteManualLocation(UUID userId, Long deviceId);
}

