package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceMediaPlaySessionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceMediaPlaySessionRepositoryAdapter implements DeviceMediaPlaySessionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT_IGNORE = """
            INSERT INTO pcc_device_media_play_session (
              user_id,
              device_id,
              media_id,
              res_origin_name,
              res_md5_name,
              item_type,
              is_lan,
              program_id,
              release_version,
              program_vsn,
              program_name_snapshot,
              vsn_md5,
              vsn_size_bytes,
              page_name,
              page_index,
              region_name,
              region_index,
              start_at,
              end_at,
              reported_duration
            )
            VALUES (
              :userId,
              :deviceId,
              :mediaId,
              :resOriginName,
              :resMd5Name,
              :itemType,
              :isLan,
              :programId,
              :releaseVersion,
              :programVsn,
              :programNameSnapshot,
              :vsnMd5,
              :vsnSizeBytes,
              :pageName,
              :pageIndex,
              :regionName,
              :regionIndex,
              :startAt,
              :endAt,
              :reportedDuration
            )
            ON CONFLICT DO NOTHING
            """;

    private static final String SQL_TOTALS = """
            SELECT
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              COUNT(DISTINCT device_id) AS device_count
            FROM pcc_device_media_play_session
            WHERE user_id = :userId
              AND period && tstzrange(:from, :to, '[)')
            """;

    private static final String SQL_SUMMARY_BASE = """
            SELECT
              media_id,
              MAX(item_type) AS item_type,
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              COUNT(DISTINCT device_id) AS device_count,
              MAX(start_at) AS last_played_at
            FROM pcc_device_media_play_session
            WHERE user_id = :userId
              AND period && tstzrange(:from, :to, '[)')
            GROUP BY media_id
            """;

    private static final String SQL_DEVICE_SUMMARY_BASE = """
            SELECT
              device_id,
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              MAX(start_at) AS last_played_at
            FROM pcc_device_media_play_session
            WHERE user_id = :userId
              AND media_id = :mediaId
              AND period && tstzrange(:from, :to, '[)')
            GROUP BY device_id
            """;

    private static final String SQL_BUCKET_BASE = """
            WITH buckets AS (
              SELECT
                (local_start AT TIME ZONE :tz) AS bucket_start_utc,
                ((local_start + (:stepInterval)::interval) AT TIME ZONE :tz) AS bucket_end_utc,
                GREATEST((local_start AT TIME ZONE :tz), :from) AS effective_start_utc,
                LEAST(((local_start + (:stepInterval)::interval) AT TIME ZONE :tz), :to) AS effective_end_utc
              FROM generate_series(
                date_trunc(:truncUnit, (:from AT TIME ZONE :tz)),
                date_trunc(:truncUnit, (:to AT TIME ZONE :tz)),
                (:stepInterval)::interval
              ) AS local_start
            )
            """;

    private static final String SQL_BUCKET = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT device_id, start_at, end_at
              FROM pcc_device_media_play_session
              WHERE user_id = :userId
                AND media_id = :mediaId
                AND period && tstzrange(:from, :to, '[)')
            )
            SELECT
              b.bucket_start_utc AS bucket_start,
              b.bucket_end_utc AS bucket_end,
              COUNT(*) FILTER (WHERE s.start_at >= b.effective_start_utc AND s.start_at < b.effective_end_utc) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(s.end_at, b.effective_end_utc) - GREATEST(s.start_at, b.effective_start_utc)))) AS BIGINT), 0) AS play_seconds,
              COUNT(DISTINCT s.device_id) AS device_count
            FROM buckets b
            LEFT JOIN sessions s
              ON s.start_at < b.effective_end_utc
             AND s.end_at > b.effective_start_utc
            GROUP BY b.bucket_start_utc, b.bucket_end_utc, b.effective_start_utc, b.effective_end_utc
            ORDER BY b.bucket_start_utc
            """;

    private static final String SQL_LIST_SESSIONS = """
            SELECT
              s.id,
              s.device_id,
              d.device_name,
              s.media_id,
              s.item_type,
              s.res_origin_name,
              s.res_md5_name,
              s.is_lan,
              s.program_id,
              s.release_version,
              s.program_vsn,
              s.program_name_snapshot,
              s.page_name,
              s.page_index,
              s.region_name,
              s.region_index,
              s.start_at,
              s.end_at,
              GREATEST(s.start_at, :from) AS effective_start_at,
              LEAST(s.end_at, :to) AS effective_end_at,
              GREATEST(0, CAST(EXTRACT(EPOCH FROM (LEAST(s.end_at, :to) - GREATEST(s.start_at, :from))) AS BIGINT)) AS play_seconds_in_range,
              s.reported_duration,
              s.created_at
            FROM pcc_device_media_play_session s
            LEFT JOIN pcc_device d
              ON d.user_id = s.user_id
             AND d.device_id = s.device_id
            WHERE s.user_id = :userId
              AND s.media_id = :mediaId
              AND s.period && tstzrange(:from, :to, '[)')
            """;

    @Override
    public int insertIgnoreBatch(List<InsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] batch = rows.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("userId", r.userId())
                        .addValue("deviceId", r.deviceId())
                        .addValue("mediaId", r.mediaId())
                        .addValue("resOriginName", r.resOriginName())
                        .addValue("resMd5Name", r.resMd5Name())
                        .addValue("itemType", r.itemType())
                        .addValue("isLan", r.lan())
                        .addValue("programId", r.programId())
                        .addValue("releaseVersion", r.releaseVersion())
                        .addValue("programVsn", r.programVsn())
                        .addValue("programNameSnapshot", r.programNameSnapshot())
                        .addValue("vsnMd5", r.vsnMd5())
                        .addValue("vsnSizeBytes", r.vsnSizeBytes())
                        .addValue("pageName", r.pageName())
                        .addValue("pageIndex", r.pageIndex())
                        .addValue("regionName", r.regionName())
                        .addValue("regionIndex", r.regionIndex())
                        .addValue("startAt", r.startAt())
                        .addValue("endAt", r.endAt())
                        .addValue("reportedDuration", r.reportedDuration()))
                .toArray(SqlParameterSource[]::new);

        int[] results = jdbcTemplate.batchUpdate(SQL_INSERT_IGNORE, batch);
        return Arrays.stream(results).sum();
    }

    @Override
    public TotalsRow totals(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to);
        return jdbcTemplate.queryForObject(SQL_TOTALS, params, (rs, rowNum) ->
                new TotalsRow(
                        Math.max(0L, rs.getLong("play_count")),
                        Math.max(0L, rs.getLong("play_seconds")),
                        Math.max(0L, rs.getLong("device_count"))
                ));
    }

    @Override
    public List<MediaSummaryRow> summarize(UUID userId, OffsetDateTime from, OffsetDateTime to, int limit, boolean orderBySeconds) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("limit", limit);

        String sql = SQL_SUMMARY_BASE
                + (orderBySeconds
                ? " ORDER BY play_seconds DESC, last_played_at DESC NULLS LAST"
                : " ORDER BY play_count DESC, last_played_at DESC NULLS LAST")
                + " LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new MediaSummaryRow(
                rs.getString("media_id"),
                rs.getString("item_type"),
                Math.max(0L, rs.getLong("play_count")),
                Math.max(0L, rs.getLong("play_seconds")),
                Math.max(0L, rs.getLong("device_count")),
                rs.getObject("last_played_at", OffsetDateTime.class)
        ));
    }

    @Override
    public List<DeviceSummaryRow> summarizeDevices(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds) {

        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("mediaId", mediaId)
                .addValue("limit", limit);

        String sql = SQL_DEVICE_SUMMARY_BASE
                + (orderBySeconds
                ? " ORDER BY play_seconds DESC, last_played_at DESC NULLS LAST"
                : " ORDER BY play_count DESC, last_played_at DESC NULLS LAST")
                + " LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new DeviceSummaryRow(
                rs.getLong("device_id"),
                Math.max(0L, rs.getLong("play_count")),
                Math.max(0L, rs.getLong("play_seconds")),
                rs.getObject("last_played_at", OffsetDateTime.class)
        ));
    }

    @Override
    public List<BucketRow> buckets(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval)
                .addValue("mediaId", mediaId);

        return jdbcTemplate.query(SQL_BUCKET, params, (rs, rowNum) -> new BucketRow(
                rs.getObject("bucket_start", OffsetDateTime.class),
                rs.getObject("bucket_end", OffsetDateTime.class),
                Math.max(0L, rs.getLong("play_count")),
                Math.max(0L, rs.getLong("play_seconds")),
                Math.max(0L, rs.getLong("device_count"))
        ));
    }

    private MapSqlParameterSource baseRangeParams(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("from", from)
                .addValue("to", to);
    }

    private MapSqlParameterSource baseBucketParams(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        return baseRangeParams(userId, from, to)
                .addValue("tz", tz)
                .addValue("truncUnit", truncUnit)
                .addValue("stepInterval", stepInterval);
    }

    @Override
    public List<SessionRow> listSessions(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorStartAt,
            Long cursorId,
            int limit) {

        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("mediaId", mediaId)
                .addValue("limit", Math.max(1, limit));

        StringBuilder sql = new StringBuilder(SQL_LIST_SESSIONS);
        if (cursorStartAt != null && cursorId != null) {
            sql.append(" AND (s.start_at < :cursorStartAt OR (s.start_at = :cursorStartAt AND s.id < :cursorId))");
            params.addValue("cursorStartAt", cursorStartAt);
            params.addValue("cursorId", cursorId);
        }
        sql.append(" ORDER BY s.start_at DESC, s.id DESC LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new SessionRow(
                rs.getLong("id"),
                rs.getLong("device_id"),
                rs.getString("device_name"),
                rs.getString("media_id"),
                rs.getString("item_type"),
                rs.getString("res_origin_name"),
                rs.getString("res_md5_name"),
                rs.getBoolean("is_lan"),
                rs.getObject("program_id", UUID.class),
                rs.getObject("release_version", Integer.class),
                rs.getString("program_vsn"),
                rs.getString("program_name_snapshot"),
                rs.getString("page_name"),
                rs.getObject("page_index", Integer.class),
                rs.getString("region_name"),
                rs.getObject("region_index", Integer.class),
                rs.getObject("start_at", OffsetDateTime.class),
                rs.getObject("end_at", OffsetDateTime.class),
                rs.getObject("effective_start_at", OffsetDateTime.class),
                rs.getObject("effective_end_at", OffsetDateTime.class),
                Math.max(0L, rs.getLong("play_seconds_in_range")),
                rs.getObject("reported_duration", Long.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }
}
