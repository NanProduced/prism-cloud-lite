package nan.produced.prism.core.export.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class CommandLogsExportRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record Cursor(OffsetDateTime createdAt, Long id) {
    }

    public record Row(
            Long id,
            UUID userId,
            Long deviceId,
            String deviceName,
            UUID operationId,
            String actionType,
            String trackingLevel,
            String status,
            boolean accepted,
            boolean covered,
            String sendMethod,
            Integer queuedId,
            String errorMessage,
            String payload,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public List<Row> list(
            UUID userId,
            OffsetDateTime fromUtc,
            OffsetDateTime toUtc,
            Long deviceId,
            UUID operationId,
            List<String> actionTypes,
            List<String> statuses,
            Boolean accepted,
            Boolean covered,
            Integer queuedId,
            String sendMethod,
            Cursor cursor,
            int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                  l.id,
                  l.user_id,
                  l.device_id,
                  d.device_name,
                  l.operation_id,
                  l.action_type,
                  l.tracking_level,
                  l.status,
                  l.accepted,
                  l.covered,
                  l.send_method,
                  l.queued_id,
                  l.error_message,
                  l.payload,
                  l.created_at,
                  l.updated_at
                FROM pcc_device_command_log l
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
        if (operationId != null) {
            sql.append(" AND l.operation_id = :operationId");
            params.addValue("operationId", operationId);
        }
        if (actionTypes != null && !actionTypes.isEmpty()) {
            sql.append(" AND l.action_type IN (:actionTypes)");
            params.addValue("actionTypes", actionTypes.stream()
                    .filter(StringUtils::hasText)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .toList());
        }
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND l.status IN (:statuses)");
            params.addValue("statuses", statuses.stream()
                    .filter(StringUtils::hasText)
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .toList());
        }
        if (accepted != null) {
            sql.append(" AND l.accepted = :accepted");
            params.addValue("accepted", accepted);
        }
        if (covered != null) {
            sql.append(" AND l.covered = :covered");
            params.addValue("covered", covered);
        }
        if (queuedId != null) {
            sql.append(" AND l.queued_id = :queuedId");
            params.addValue("queuedId", queuedId);
        }
        if (StringUtils.hasText(sendMethod)) {
            sql.append(" AND LOWER(l.send_method) = :sendMethod");
            params.addValue("sendMethod", sendMethod.trim().toLowerCase(Locale.ROOT));
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
                rs.getObject("operation_id", UUID.class),
                rs.getString("action_type"),
                rs.getString("tracking_level"),
                rs.getString("status"),
                rs.getBoolean("accepted"),
                rs.getBoolean("covered"),
                rs.getString("send_method"),
                rs.getObject("queued_id", Integer.class),
                rs.getString("error_message"),
                rs.getString("payload"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        ));
    }
}

