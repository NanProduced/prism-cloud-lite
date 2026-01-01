package nan.produced.prism.core.export.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceLogsExportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record Cursor(OffsetDateTime createdAt, Long id) {
    }

    public record Row(
            Long id,
            UUID userId,
            Long deviceId,
            String deviceName,
            Integer operationId,
            Integer level,
            String logType,
            String subtype1,
            String subtype2,
            String subtype3,
            String categories,
            String description,
            String deviceTimeRaw,
            Integer handleStatus,
            String handleTimeRaw,
            String logArg1,
            String logArg2,
            String logArg3,
            String logArg4,
            String logArg5,
            String logArg6,
            String others,
            OffsetDateTime reportTime,
            OffsetDateTime createdAt
    ) {
    }

    public List<Row> list(
            UUID userId,
            OffsetDateTime fromUtc,
            OffsetDateTime toUtc,
            Long deviceId,
            List<Integer> operationIds,
            Cursor cursor,
            int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                  l.id,
                  l.user_id,
                  l.device_id,
                  d.device_name,
                  l.operation_id,
                  l.level,
                  l.log_type,
                  l.log_subtype1,
                  l.log_subtype2,
                  l.log_subtype3,
                  l.categories,
                  l.description,
                  l.device_time_raw,
                  l.hand_status,
                  l.hand_time_raw,
                  l.log_arg1,
                  l.log_arg2,
                  l.log_arg3,
                  l.log_arg4,
                  l.log_arg5,
                  l.log_arg6,
                  l.others,
                  l.report_time,
                  l.created_at
                FROM pcc_device_log l
                LEFT JOIN pcc_device d
                  ON d.user_id = l.user_id
                 AND d.device_id = l.device_id
                WHERE l.user_id = :userId
                  AND l.created_at >= :from
                  AND l.created_at <= :to
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("from", fromUtc)
                .addValue("to", toUtc)
                .addValue("limit", Math.max(1, limit));

        if (deviceId != null) {
            sql.append(" AND l.device_id = :deviceId");
            params.addValue("deviceId", deviceId);
        }
        if (operationIds != null && !operationIds.isEmpty()) {
            sql.append(" AND l.operation_id IN (:operationIds)");
            params.addValue("operationIds", operationIds);
        }
        if (cursor != null && cursor.createdAt() != null && cursor.id() != null) {
            sql.append(" AND (l.created_at < :cursorCreatedAt OR (l.created_at = :cursorCreatedAt AND l.id < :cursorId))");
            params.addValue("cursorCreatedAt", cursor.createdAt());
            params.addValue("cursorId", cursor.id());
        }

        sql.append(" ORDER BY l.created_at DESC, l.id DESC LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new Row(
                rs.getLong("id"),
                rs.getObject("user_id", UUID.class),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getObject("operation_id", Integer.class),
                rs.getObject("level", Integer.class),
                rs.getString("log_type"),
                rs.getString("log_subtype1"),
                rs.getString("log_subtype2"),
                rs.getString("log_subtype3"),
                rs.getString("categories"),
                rs.getString("description"),
                rs.getString("device_time_raw"),
                rs.getObject("hand_status", Integer.class),
                rs.getString("hand_time_raw"),
                rs.getString("log_arg1"),
                rs.getString("log_arg2"),
                rs.getString("log_arg3"),
                rs.getString("log_arg4"),
                rs.getString("log_arg5"),
                rs.getString("log_arg6"),
                rs.getString("others"),
                rs.getObject("report_time", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }
}

