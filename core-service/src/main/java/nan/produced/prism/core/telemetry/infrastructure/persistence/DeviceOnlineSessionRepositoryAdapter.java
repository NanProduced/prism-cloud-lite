package nan.produced.prism.core.telemetry.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceOnlineSessionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeviceOnlineSessionRepositoryAdapter implements DeviceOnlineSessionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT_IGNORE = """
            INSERT INTO pc_device_online_session (user_id, device_id, online_at, offline_at)
            VALUES (:userId, :deviceId, :onlineAt, :offlineAt)
            ON CONFLICT (device_id, online_at, offline_at) DO NOTHING
            """;

    private static final String SQL_SUM_BY_DEVICE = """
            SELECT device_id,
                   COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(offline_at, :to) - GREATEST(online_at, :from)))) AS BIGINT), 0) AS online_seconds
            FROM pc_device_online_session
            WHERE user_id = :userId
              AND period && tstzrange(:from, :to, '[)')
            GROUP BY device_id
            ORDER BY device_id
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

    private static final String SQL_BUCKET_FOR_DEVICE = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT online_at, offline_at
              FROM pc_device_online_session
              WHERE user_id = :userId
                AND device_id = :deviceId
                AND period && tstzrange(:from, :to, '[)')
            )
            SELECT
              b.bucket_start_utc AS bucket_start,
              b.bucket_end_utc AS bucket_end,
              CAST(EXTRACT(EPOCH FROM (b.effective_end_utc - b.effective_start_utc)) AS BIGINT) AS bucket_seconds,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(s.offline_at, b.effective_end_utc) - GREATEST(s.online_at, b.effective_start_utc)))) AS BIGINT), 0) AS online_seconds
            FROM buckets b
            LEFT JOIN sessions s
              ON s.online_at < b.effective_end_utc
             AND s.offline_at > b.effective_start_utc
            GROUP BY b.bucket_start_utc, b.bucket_end_utc, b.effective_start_utc, b.effective_end_utc
            ORDER BY b.bucket_start_utc
            """;

    private static final String SQL_ACTIVE_DEVICES_BY_BUCKET = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT device_id, online_at, offline_at
              FROM pc_device_online_session
              WHERE user_id = :userId
                AND period && tstzrange(:from, :to, '[)')
            )
            SELECT
              b.bucket_start_utc AS bucket_start,
              b.bucket_end_utc AS bucket_end,
              CAST(EXTRACT(EPOCH FROM (b.effective_end_utc - b.effective_start_utc)) AS BIGINT) AS bucket_seconds,
              COUNT(DISTINCT s.device_id) AS active_devices
            FROM buckets b
            LEFT JOIN sessions s
              ON s.online_at < b.effective_end_utc
             AND s.offline_at > b.effective_start_utc
            GROUP BY b.bucket_start_utc, b.bucket_end_utc, b.effective_start_utc, b.effective_end_utc
            ORDER BY b.bucket_start_utc
            """;

    private static final String SQL_CONCURRENCY_BY_BUCKET = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT online_at, offline_at
              FROM pc_device_online_session
              WHERE user_id = :userId
                AND period && tstzrange(:from, :to, '[)')
            )
            , bucket_totals AS (
              SELECT
                b.bucket_start_utc,
                b.bucket_end_utc,
                CAST(EXTRACT(EPOCH FROM (b.effective_end_utc - b.effective_start_utc)) AS BIGINT) AS bucket_seconds,
                COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(s.offline_at, b.effective_end_utc) - GREATEST(s.online_at, b.effective_start_utc)))) AS BIGINT), 0) AS total_online_device_seconds
              FROM buckets b
              LEFT JOIN sessions s
                ON s.online_at < b.effective_end_utc
               AND s.offline_at > b.effective_start_utc
              GROUP BY b.bucket_start_utc, b.bucket_end_utc, b.effective_start_utc, b.effective_end_utc
            )
            , segments AS (
              SELECT
                b.bucket_start_utc,
                b.bucket_end_utc,
                GREATEST(s.online_at, b.effective_start_utc) AS seg_start,
                LEAST(s.offline_at, b.effective_end_utc) AS seg_end
              FROM buckets b
              JOIN sessions s
                ON s.online_at < b.effective_end_utc
               AND s.offline_at > b.effective_start_utc
              WHERE GREATEST(s.online_at, b.effective_start_utc) < LEAST(s.offline_at, b.effective_end_utc)
            )
            , events AS (
              SELECT bucket_start_utc, bucket_end_utc, seg_start AS t, 1 AS delta FROM segments
              UNION ALL
              SELECT bucket_start_utc, bucket_end_utc, seg_end AS t, -1 AS delta FROM segments
            )
            , ordered AS (
              SELECT
                bucket_start_utc,
                bucket_end_utc,
                t,
                SUM(delta) OVER (PARTITION BY bucket_start_utc, bucket_end_utc ORDER BY t, delta) AS concurrent
              FROM events
            )
            , bucket_max AS (
              SELECT bucket_start_utc, bucket_end_utc, MAX(concurrent) AS max_concurrent
              FROM ordered
              GROUP BY bucket_start_utc, bucket_end_utc
            )
            SELECT
              t.bucket_start_utc AS bucket_start,
              t.bucket_end_utc AS bucket_end,
              t.bucket_seconds,
              t.total_online_device_seconds,
              COALESCE(m.max_concurrent, 0) AS max_concurrent
            FROM bucket_totals t
            LEFT JOIN bucket_max m
              ON m.bucket_start_utc = t.bucket_start_utc
             AND m.bucket_end_utc = t.bucket_end_utc
            ORDER BY t.bucket_start_utc
            """;

    private static final String SQL_SESSION_STATS_FOR_DEVICE = """
            WITH sessions AS (
              SELECT
                GREATEST(online_at, :from) AS effective_online_at,
                LEAST(offline_at, :to) AS effective_offline_at
              FROM pc_device_online_session
              WHERE user_id = :userId
                AND device_id = :deviceId
                AND period && tstzrange(:from, :to, '[)')
            )
            , overlaps AS (
              SELECT
                GREATEST(0, EXTRACT(EPOCH FROM (effective_offline_at - effective_online_at))) AS overlap_seconds
              FROM sessions
              WHERE effective_online_at < effective_offline_at
            )
            SELECT
              COALESCE(COUNT(*), 0) AS session_count,
              COALESCE(CAST(SUM(overlap_seconds) AS BIGINT), 0) AS total_online_seconds,
              COALESCE(AVG(overlap_seconds), 0) AS avg_session_seconds,
              COALESCE(CAST(MAX(overlap_seconds) AS BIGINT), 0) AS max_session_seconds,
              COALESCE(CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY overlap_seconds) AS BIGINT), 0) AS p95_session_seconds
            FROM overlaps
            """;

    private static final String SQL_OFFLINE_GAP_STATS_FOR_DEVICE = """
            WITH sessions AS (
              SELECT
                GREATEST(online_at, :from) AS effective_online_at,
                LEAST(offline_at, :to) AS effective_offline_at
              FROM pc_device_online_session
              WHERE user_id = :userId
                AND device_id = :deviceId
                AND period && tstzrange(:from, :to, '[)')
            )
            , ordered AS (
              SELECT
                effective_online_at,
                effective_offline_at,
                LAG(effective_offline_at) OVER (ORDER BY effective_online_at, effective_offline_at) AS prev_offline_at
              FROM sessions
              WHERE effective_online_at < effective_offline_at
            )
            , gaps AS (
              SELECT
                EXTRACT(EPOCH FROM (effective_online_at - prev_offline_at)) AS gap_seconds
              FROM ordered
              WHERE prev_offline_at IS NOT NULL
                AND effective_online_at > prev_offline_at
            )
            SELECT
              COALESCE(COUNT(*), 0) AS gap_count,
              COALESCE(CAST(SUM(gap_seconds) AS BIGINT), 0) AS total_gap_seconds,
              COALESCE(AVG(gap_seconds), 0) AS avg_gap_seconds,
              COALESCE(CAST(MAX(gap_seconds) AS BIGINT), 0) AS max_gap_seconds
            FROM gaps
            """;

    private static final String SQL_LIST_SESSIONS_FOR_DEVICE = """
            SELECT
              id AS session_id,
              online_at,
              offline_at,
              GREATEST(online_at, :from) AS effective_online_at,
              LEAST(offline_at, :to) AS effective_offline_at,
              COALESCE(CAST(EXTRACT(EPOCH FROM (LEAST(offline_at, :to) - GREATEST(online_at, :from))) AS BIGINT), 0) AS online_seconds_in_range
            FROM pc_device_online_session
            WHERE user_id = :userId
              AND device_id = :deviceId
              AND period && tstzrange(:from, :to, '[)')
              AND (:cursor IS NULL OR online_at < :cursor)
            ORDER BY online_at DESC, id DESC
            LIMIT :limit
            """;

    @Override
    public boolean insertIgnore(UUID userId, Long deviceId, OffsetDateTime onlineAt, OffsetDateTime offlineAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("deviceId", deviceId)
                .addValue("onlineAt", onlineAt)
                .addValue("offlineAt", offlineAt);

        return jdbcTemplate.update(SQL_INSERT_IGNORE, params) > 0;
    }

    @Override
    public List<OnlineSecondsByDeviceRow> sumOnlineSecondsByDevice(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to);
        return jdbcTemplate.query(SQL_SUM_BY_DEVICE, params, (rs, rowNum) ->
                new OnlineSecondsByDeviceRow(rs.getLong("device_id"), rs.getLong("online_seconds")));
    }

    @Override
    public List<OnlineSecondsBucketRow> sumOnlineSecondsByBucketForDevice(
            UUID userId,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval)
                .addValue("deviceId", deviceId);

        return jdbcTemplate.query(SQL_BUCKET_FOR_DEVICE, params, (rs, rowNum) ->
                new OnlineSecondsBucketRow(
                        rs.getObject("bucket_start", OffsetDateTime.class),
                        rs.getObject("bucket_end", OffsetDateTime.class),
                        rs.getLong("bucket_seconds"),
                        rs.getLong("online_seconds")
                ));
    }

    @Override
    public List<ActiveDeviceCountBucketRow> countActiveDevicesByBucket(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval);

        return jdbcTemplate.query(SQL_ACTIVE_DEVICES_BY_BUCKET, params, (rs, rowNum) ->
                new ActiveDeviceCountBucketRow(
                        rs.getObject("bucket_start", OffsetDateTime.class),
                        rs.getObject("bucket_end", OffsetDateTime.class),
                        rs.getLong("bucket_seconds"),
                        rs.getLong("active_devices")
                ));
    }

    @Override
    public List<ConcurrencyBucketRow> concurrencyByBucket(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval);

        return jdbcTemplate.query(SQL_CONCURRENCY_BY_BUCKET, params, (rs, rowNum) ->
                new ConcurrencyBucketRow(
                        rs.getObject("bucket_start", OffsetDateTime.class),
                        rs.getObject("bucket_end", OffsetDateTime.class),
                        rs.getLong("bucket_seconds"),
                        rs.getLong("total_online_device_seconds"),
                        rs.getLong("max_concurrent")
                ));
    }

    @Override
    public SessionStatsRow sessionStatsForDevice(UUID userId, Long deviceId, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("deviceId", deviceId);

        return jdbcTemplate.queryForObject(SQL_SESSION_STATS_FOR_DEVICE, params, (rs, rowNum) ->
                new SessionStatsRow(
                        rs.getLong("session_count"),
                        rs.getLong("total_online_seconds"),
                        rs.getDouble("avg_session_seconds"),
                        rs.getLong("max_session_seconds"),
                        rs.getLong("p95_session_seconds")
                ));
    }

    @Override
    public OfflineGapStatsRow offlineGapStatsForDevice(UUID userId, Long deviceId, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("deviceId", deviceId);

        return jdbcTemplate.queryForObject(SQL_OFFLINE_GAP_STATS_FOR_DEVICE, params, (rs, rowNum) ->
                new OfflineGapStatsRow(
                        rs.getLong("gap_count"),
                        rs.getLong("total_gap_seconds"),
                        rs.getDouble("avg_gap_seconds"),
                        rs.getLong("max_gap_seconds")
                ));
    }

    @Override
    public List<SessionItemRow> listSessionsForDevice(
            UUID userId,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursor,
            int limit) {

        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("deviceId", deviceId)
                .addValue("cursor", cursor)
                .addValue("limit", limit);

        return jdbcTemplate.query(SQL_LIST_SESSIONS_FOR_DEVICE, params, (rs, rowNum) ->
                new SessionItemRow(
                        rs.getLong("session_id"),
                        rs.getObject("online_at", OffsetDateTime.class),
                        rs.getObject("offline_at", OffsetDateTime.class),
                        rs.getObject("effective_online_at", OffsetDateTime.class),
                        rs.getObject("effective_offline_at", OffsetDateTime.class),
                        rs.getLong("online_seconds_in_range")
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
}

