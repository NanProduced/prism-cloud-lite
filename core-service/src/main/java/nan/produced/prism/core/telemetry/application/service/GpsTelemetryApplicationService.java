package nan.produced.prism.core.telemetry.application.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.telemetry.api.GpsTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsDeviceLocationItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsHeatmapCellItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsPointItem;
import nan.produced.prism.core.telemetry.api.dto.gps.ManualLocationItem;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceGpsPointRepository;
import nan.produced.prism.core.telemetry.application.support.TelemetryQueryWindow;
import nan.produced.prism.core.telemetry.domain.DeviceLocationOverrideEntity;
import nan.produced.prism.core.telemetry.infrastructure.persistence.DeviceLocationOverrideRepositoryJpa;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GpsTelemetryApplicationService implements GpsTelemetryFacade {

    private static final int RETENTION_DAYS = 30;
    private static final int DEFAULT_TRACK_LIMIT = 5000;
    private static final int MAX_TRACK_LIMIT = 50000;
    private static final int DEFAULT_HEATMAP_LIMIT = 2000;
    private static final int MAX_HEATMAP_LIMIT = 20000;

    private static final String SQL_VALIDATE_DEVICE_OWNERSHIP = """
            SELECT 1
            FROM device
            WHERE device_id = :deviceId
              AND user_id = :userId
            """;

    private final DeviceGpsPointRepository deviceGpsPointRepository;
    private final DeviceLocationOverrideRepositoryJpa deviceLocationOverrideRepositoryJpa;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<GpsDeviceLocationItem> listLatestLocations(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        Map<Long, ManualLocationItem> manualByDevice = new HashMap<>();
        for (DeviceLocationOverrideEntity entity : deviceLocationOverrideRepositoryJpa.findByUserId(userId)) {
            if (entity == null || entity.getDeviceId() == null || entity.getLongitude() == null || entity.getLatitude() == null) {
                continue;
            }
            manualByDevice.put(entity.getDeviceId(), new ManualLocationItem(
                entity.getLongitude(),
                entity.getLatitude(),
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant() : null
            ));
        }

        List<DeviceGpsPointRepository.PointRow> latest = deviceGpsPointRepository.listLatestPerDevice(userId);
        Map<Long, GpsPointItem> reportedByDevice = new HashMap<>();
        for (DeviceGpsPointRepository.PointRow row : latest) {
            if (row == null || row.deviceId() == null) {
                continue;
            }
            reportedByDevice.put(row.deviceId(), toGpsPointItem(row));
        }

        Map<Long, GpsDeviceLocationItem> merged = new HashMap<>();
        manualByDevice.forEach((deviceId, manual) ->
            merged.put(deviceId, new GpsDeviceLocationItem(deviceId, reportedByDevice.get(deviceId), manual)));
        reportedByDevice.forEach((deviceId, reported) ->
            merged.putIfAbsent(deviceId, new GpsDeviceLocationItem(deviceId, reported, manualByDevice.get(deviceId))));

        return merged.values().stream()
            .sorted((a, b) -> Long.compare(a.deviceId(), b.deviceId()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GpsPointItem> listTrack(UUID userId, Long deviceId, Instant from, Instant to, Integer limit) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }

        TelemetryQueryWindow.TimeWindow window = TelemetryQueryWindow.clamp(from, to, RETENTION_DAYS, 1);
        if (window.to().isBefore(window.from())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }
        int safeLimit = clampLimit(limit, DEFAULT_TRACK_LIMIT, MAX_TRACK_LIMIT);

        List<DeviceGpsPointRepository.PointRow> rows = deviceGpsPointRepository.listTrack(
            userId,
            deviceId,
            window.from(),
            window.to(),
            safeLimit
        );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<GpsPointItem> list = new ArrayList<>(rows.size());
        for (DeviceGpsPointRepository.PointRow row : rows) {
            if (row == null) {
                continue;
            }
            list.add(toGpsPointItem(row));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GpsHeatmapCellItem> heatmap(UUID userId, Instant from, Instant to, int precision, Integer limit) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        TelemetryQueryWindow.TimeWindow window = TelemetryQueryWindow.clamp(from, to, RETENTION_DAYS, 1);
        if (window.to().isBefore(window.from())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }
        int safeLimit = clampLimit(limit, DEFAULT_HEATMAP_LIMIT, MAX_HEATMAP_LIMIT);

        List<DeviceGpsPointRepository.HeatmapCellRow> rows = deviceGpsPointRepository.heatmap(
            userId,
            window.from(),
            window.to(),
            precision,
            safeLimit
        );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
            .map(r -> new GpsHeatmapCellItem(r.longitude(), r.latitude(), r.count()))
            .toList();
    }

    @Override
    @Transactional
    public ManualLocationItem upsertManualLocation(UUID userId, Long deviceId, double longitude, double latitude) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }
        if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid longitude/latitude");
        }

        validateDeviceOwnership(userId, deviceId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DeviceLocationOverrideEntity entity = deviceLocationOverrideRepositoryJpa.findByUserIdAndDeviceId(userId, deviceId)
            .orElseGet(() -> DeviceLocationOverrideEntity.builder()
                .userId(userId)
                .deviceId(deviceId)
                .build());

        entity.setLongitude(longitude);
        entity.setLatitude(latitude);
        entity.setUpdatedAt(now);

        DeviceLocationOverrideEntity saved = deviceLocationOverrideRepositoryJpa.save(entity);
        return new ManualLocationItem(saved.getLongitude(), saved.getLatitude(), now.toInstant());
    }

    @Override
    @Transactional
    public void deleteManualLocation(UUID userId, Long deviceId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            return;
        }
        deviceLocationOverrideRepositoryJpa.findByUserIdAndDeviceId(userId, deviceId)
            .ifPresent(deviceLocationOverrideRepositoryJpa::delete);
    }

    private void validateDeviceOwnership(UUID userId, Long deviceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("deviceId", deviceId);
        List<Integer> list = jdbcTemplate.query(SQL_VALIDATE_DEVICE_OWNERSHIP, params, (rs, rowNum) -> rs.getInt(1));
        if (list == null || list.isEmpty()) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
    }

    private GpsPointItem toGpsPointItem(DeviceGpsPointRepository.PointRow row) {
        Instant at = row.serverTime() != null ? row.serverTime().toInstant() : null;
        return new GpsPointItem(
            row.longitude(),
            row.latitude(),
            row.accuracy(),
            row.altitude(),
            row.speed(),
            row.direct(),
            row.satellites(),
            at
        );
    }

    private int clampLimit(Integer limit, int defaultLimit, int maxLimit) {
        int value = limit == null ? defaultLimit : limit;
        if (value <= 0) {
            value = defaultLimit;
        }
        return Math.min(maxLimit, value);
    }
}
