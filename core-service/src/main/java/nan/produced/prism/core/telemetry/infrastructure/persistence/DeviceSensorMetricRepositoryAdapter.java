package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceSensorMetricRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class DeviceSensorMetricRepositoryAdapter implements DeviceSensorMetricRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT = """
            INSERT INTO pc_device_sensor_metric (
              user_id,
              device_id,
              source_type,
              report_type,
              sensor_type,
              sensor_id,
              metric_key,
              value_num,
              report_time_raw,
              server_time
            )
            VALUES (
              :userId,
              :deviceId,
              :sourceType,
              :reportType,
              :sensorType,
              :sensorId,
              :metricKey,
              :valueNum,
              :reportTimeRaw,
              :serverTime
            )
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
                .addValue("sourceType", r.sourceType())
                .addValue("reportType", r.reportType())
                .addValue("sensorType", r.sensorType())
                .addValue("sensorId", r.sensorId())
                .addValue("metricKey", r.metricKey())
                .addValue("valueNum", r.valueNum())
                .addValue("reportTimeRaw", r.reportTimeRaw())
                .addValue("serverTime", r.serverTime()))
            .toArray(SqlParameterSource[]::new);

        int[] results = jdbcTemplate.batchUpdate(SQL_INSERT, batch);
        return Arrays.stream(results).sum();
    }

    @Override
    public List<PointRow> listPoints(UUID userId,
                                     Long deviceId,
                                     java.time.OffsetDateTime from,
                                     java.time.OffsetDateTime to,
                                     String sourceType,
                                     List<String> reportTypes,
                                     List<String> metricKeys,
                                     Integer limit) {

        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");

        StringBuilder sql = new StringBuilder("""
                SELECT source_type, report_type, metric_key, server_time, value_num
                FROM pc_device_sensor_metric
                WHERE user_id = :userId
                  AND device_id = :deviceId
                  AND server_time >= :from
                  AND server_time < :to
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("deviceId", deviceId)
            .addValue("from", from)
            .addValue("to", to);

        if (StringUtils.hasText(sourceType)) {
            sql.append(" AND source_type = :sourceType");
            params.addValue("sourceType", sourceType.trim());
        }

        if (reportTypes != null && !reportTypes.isEmpty()) {
            List<String> normalized = reportTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
            if (!normalized.isEmpty()) {
                sql.append(" AND report_type IN (:reportTypes)");
                params.addValue("reportTypes", normalized);
            }
        }

        if (metricKeys != null && !metricKeys.isEmpty()) {
            List<String> normalized = metricKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
            if (!normalized.isEmpty()) {
                sql.append(" AND metric_key IN (:metricKeys)");
                params.addValue("metricKeys", normalized);
            }
        }

        sql.append(" ORDER BY server_time ASC, id ASC");

        if (limit != null && limit > 0) {
            sql.append(" LIMIT :limit");
            params.addValue("limit", limit);
        }

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new PointRow(
            rs.getString("source_type"),
            rs.getString("report_type"),
            rs.getString("metric_key"),
            rs.getObject("server_time", java.time.OffsetDateTime.class),
            (Double) rs.getObject("value_num")
        ));
    }
}
