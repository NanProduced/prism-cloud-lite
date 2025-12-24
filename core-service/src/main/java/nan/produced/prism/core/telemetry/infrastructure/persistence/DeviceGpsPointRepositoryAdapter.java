package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceGpsPointRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceGpsPointRepositoryAdapter implements DeviceGpsPointRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT = """
            INSERT INTO pcc_device_gps_point (
              user_id,
              device_id,
              longitude,
              latitude,
              accuracy,
              altitude,
              speed,
              direct,
              satellites,
              report_time_raw,
              server_time,
              extra
            )
            VALUES (
              :userId,
              :deviceId,
              :longitude,
              :latitude,
              :accuracy,
              :altitude,
              :speed,
              :direct,
              :satellites,
              :reportTimeRaw,
              :serverTime,
              CAST(:extraJson AS jsonb)
            )
            """;

    private static final String SQL_LATEST_PER_DEVICE = """
            SELECT DISTINCT ON (device_id)
              device_id,
              longitude,
              latitude,
              accuracy,
              altitude,
              speed,
              direct,
              satellites,
              server_time
            FROM pcc_device_gps_point
            WHERE user_id = :userId
            ORDER BY device_id, server_time DESC
            """;

    private static final String SQL_TRACK_BASE = """
            SELECT
              device_id,
              longitude,
              latitude,
              accuracy,
              altitude,
              speed,
              direct,
              satellites,
              server_time
            FROM pcc_device_gps_point
            WHERE user_id = :userId
              AND device_id = :deviceId
              AND server_time >= :from
              AND server_time < :to
            ORDER BY server_time ASC, id ASC
            """;

    private static final String SQL_HEATMAP_BASE = """
            SELECT
              FLOOR(longitude * :factor) / :factor AS lon_bucket,
              FLOOR(latitude * :factor) / :factor AS lat_bucket,
              COUNT(*) AS point_count
            FROM pcc_device_gps_point
            WHERE user_id = :userId
              AND server_time >= :from
              AND server_time < :to
            GROUP BY lon_bucket, lat_bucket
            ORDER BY point_count DESC
            """;

    @Override
    public int insertBatch(List<InsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        SqlParameterSource[] batch = rows.stream()
            .map(r -> new MapSqlParameterSource()
                .addValue("userId", r.userId())
                .addValue("deviceId", r.deviceId())
                .addValue("longitude", r.longitude())
                .addValue("latitude", r.latitude())
                .addValue("accuracy", r.accuracy())
                .addValue("altitude", r.altitude())
                .addValue("speed", r.speed())
                .addValue("direct", r.direct())
                .addValue("satellites", r.satellites())
                .addValue("reportTimeRaw", r.reportTimeRaw())
                .addValue("serverTime", r.serverTime())
                .addValue("extraJson", r.extraJson()))
            .toArray(SqlParameterSource[]::new);

        int[] results = jdbcTemplate.batchUpdate(SQL_INSERT, batch);
        return Arrays.stream(results).sum();
    }

    @Override
    public List<PointRow> listLatestPerDevice(UUID userId) {
        Objects.requireNonNull(userId, "userId is required");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId);
        return jdbcTemplate.query(SQL_LATEST_PER_DEVICE, params, (rs, rowNum) -> new PointRow(
            rs.getLong("device_id"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getObject("accuracy", Float.class),
            rs.getObject("altitude", Float.class),
            rs.getObject("speed", Float.class),
            (Double) rs.getObject("direct"),
            (Integer) rs.getObject("satellites"),
            rs.getObject("server_time", java.time.OffsetDateTime.class)
        ));
    }

    @Override
    public List<PointRow> listTrack(UUID userId, Long deviceId, java.time.OffsetDateTime from, java.time.OffsetDateTime to, Integer limit) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");

        String sql = SQL_TRACK_BASE + ((limit != null && limit > 0) ? " LIMIT :limit" : "");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("deviceId", deviceId)
            .addValue("from", from)
            .addValue("to", to);
        if (limit != null && limit > 0) {
            params.addValue("limit", limit);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new PointRow(
            rs.getLong("device_id"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getObject("accuracy", Float.class),
            rs.getObject("altitude", Float.class),
            rs.getObject("speed", Float.class),
            (Double) rs.getObject("direct"),
            (Integer) rs.getObject("satellites"),
            rs.getObject("server_time", java.time.OffsetDateTime.class)
        ));
    }

    @Override
    public List<HeatmapCellRow> heatmap(UUID userId, java.time.OffsetDateTime from, java.time.OffsetDateTime to, int precision, Integer limit) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");

        int safePrecision = Math.min(6, Math.max(0, precision));
        double factor = Math.pow(10D, safePrecision);

        String sql = SQL_HEATMAP_BASE + ((limit != null && limit > 0) ? " LIMIT :limit" : "");
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("from", from)
            .addValue("to", to)
            .addValue("factor", factor);
        if (limit != null && limit > 0) {
            params.addValue("limit", limit);
        }

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new HeatmapCellRow(
            rs.getDouble("lon_bucket"),
            rs.getDouble("lat_bucket"),
            Math.max(0L, rs.getLong("point_count"))
        ));
    }
}
