package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceProgramPlaySessionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DeviceProgramPlaySessionRepositoryAdapter implements DeviceProgramPlaySessionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL_INSERT_IGNORE = """
            INSERT INTO pcc_device_program_play_session (
              user_id,
              device_id,
              is_lan,
              lan_program_id,
              program_id,
              release_version,
              program_vsn,
              program_name_snapshot,
              vsn_md5,
              vsn_size_bytes,
              start_at,
              end_at
            )
            VALUES (
              :userId,
              :deviceId,
              :isLan,
              :lanProgramId,
              :programId,
              :releaseVersion,
              :programVsn,
              :programNameSnapshot,
              :vsnMd5,
              :vsnSizeBytes,
              :startAt,
              :endAt
            )
            ON CONFLICT DO NOTHING
            """;

    private static final String SQL_TOTALS = """
            SELECT
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              COUNT(DISTINCT device_id) AS device_count
            FROM pcc_device_program_play_session
            WHERE user_id = :userId
              AND period && tstzrange(:from, :to, '[)')
            """;

    private static final String SQL_SUMMARY_BASE = """
            SELECT
              is_lan,
              program_id,
              release_version,
              lan_program_id,
              MAX(program_name_snapshot) AS program_name_snapshot,
              MAX(program_vsn) AS program_vsn,
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              COUNT(DISTINCT device_id) AS device_count,
              MAX(start_at) AS last_played_at
            FROM pcc_device_program_play_session
            WHERE user_id = :userId
              AND period && tstzrange(:from, :to, '[)')
            GROUP BY is_lan, program_id, release_version, lan_program_id
            """;

    private static final String SQL_DEVICE_SUMMARY_PLATFORM_BASE = """
            SELECT
              device_id,
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              MAX(start_at) AS last_played_at
            FROM pcc_device_program_play_session
            WHERE user_id = :userId
              AND is_lan = FALSE
              AND program_id = :programId
              AND release_version = :releaseVersion
              AND period && tstzrange(:from, :to, '[)')
            GROUP BY device_id
            """;

    private static final String SQL_DEVICE_SUMMARY_LAN_BASE = """
            SELECT
              device_id,
              COUNT(*) FILTER (WHERE start_at >= :from AND start_at < :to) AS play_count,
              COALESCE(CAST(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :to) - GREATEST(start_at, :from)))) AS BIGINT), 0) AS play_seconds,
              MAX(start_at) AS last_played_at
            FROM pcc_device_program_play_session
            WHERE user_id = :userId
              AND is_lan = TRUE
              AND lan_program_id = :lanProgramId
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

    private static final String SQL_BUCKET_PLATFORM = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT device_id, start_at, end_at
              FROM pcc_device_program_play_session
              WHERE user_id = :userId
                AND is_lan = FALSE
                AND program_id = :programId
                AND release_version = :releaseVersion
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

    private static final String SQL_BUCKET_LAN = SQL_BUCKET_BASE + """
            , sessions AS (
              SELECT device_id, start_at, end_at
              FROM pcc_device_program_play_session
              WHERE user_id = :userId
                AND is_lan = TRUE
                AND lan_program_id = :lanProgramId
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

    @Override
    public int insertIgnoreBatch(List<InsertRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] batch = rows.stream()
                .map(r -> new MapSqlParameterSource()
                        .addValue("userId", r.userId())
                        .addValue("deviceId", r.deviceId())
                        .addValue("isLan", r.lan())
                        .addValue("lanProgramId", r.lanProgramId())
                        .addValue("programId", r.programId())
                        .addValue("releaseVersion", r.releaseVersion())
                        .addValue("programVsn", r.programVsn())
                        .addValue("programNameSnapshot", r.programNameSnapshot())
                        .addValue("vsnMd5", r.vsnMd5())
                        .addValue("vsnSizeBytes", r.vsnSizeBytes())
                        .addValue("startAt", r.startAt())
                        .addValue("endAt", r.endAt()))
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
    public List<ProgramSummaryRow> summarize(UUID userId, OffsetDateTime from, OffsetDateTime to, int limit, boolean orderBySeconds) {
        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("limit", limit);

        String sql = SQL_SUMMARY_BASE
                + (orderBySeconds
                ? " ORDER BY play_seconds DESC, last_played_at DESC NULLS LAST"
                : " ORDER BY play_count DESC, last_played_at DESC NULLS LAST")
                + " LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ProgramSummaryRow(
                rs.getBoolean("is_lan"),
                rs.getObject("program_id", UUID.class),
                rs.getObject("release_version", Integer.class),
                rs.getString("lan_program_id"),
                rs.getString("program_name_snapshot"),
                rs.getString("program_vsn"),
                Math.max(0L, rs.getLong("play_count")),
                Math.max(0L, rs.getLong("play_seconds")),
                Math.max(0L, rs.getLong("device_count")),
                rs.getObject("last_played_at", OffsetDateTime.class)
        ));
    }

    @Override
    public List<DeviceSummaryRow> summarizeDevicesForPlatform(
            UUID userId,
            UUID programId,
            int releaseVersion,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds) {

        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("programId", programId)
                .addValue("releaseVersion", releaseVersion)
                .addValue("limit", limit);

        String sql = SQL_DEVICE_SUMMARY_PLATFORM_BASE
                + (orderBySeconds
                ? " ORDER BY play_seconds DESC, last_played_at DESC NULLS LAST"
                : " ORDER BY play_count DESC, last_played_at DESC NULLS LAST")
                + " LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new DeviceSummaryRow(
                        rs.getLong("device_id"),
                        Math.max(0L, rs.getLong("play_count")),
                        Math.max(0L, rs.getLong("play_seconds")),
                        rs.getObject("last_played_at", OffsetDateTime.class)
                ));
    }

    @Override
    public List<DeviceSummaryRow> summarizeDevicesForLan(
            UUID userId,
            String lanProgramId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds) {

        MapSqlParameterSource params = baseRangeParams(userId, from, to)
                .addValue("lanProgramId", lanProgramId)
                .addValue("limit", limit);

        String sql = SQL_DEVICE_SUMMARY_LAN_BASE
                + (orderBySeconds
                ? " ORDER BY play_seconds DESC, last_played_at DESC NULLS LAST"
                : " ORDER BY play_count DESC, last_played_at DESC NULLS LAST")
                + " LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new DeviceSummaryRow(
                        rs.getLong("device_id"),
                        Math.max(0L, rs.getLong("play_count")),
                        Math.max(0L, rs.getLong("play_seconds")),
                        rs.getObject("last_played_at", OffsetDateTime.class)
                ));
    }

    @Override
    public List<BucketRow> bucketsForPlatform(
            UUID userId,
            UUID programId,
            int releaseVersion,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval)
                .addValue("programId", programId)
                .addValue("releaseVersion", releaseVersion);

        return jdbcTemplate.query(SQL_BUCKET_PLATFORM, params, (rs, rowNum) -> new BucketRow(
                rs.getObject("bucket_start", OffsetDateTime.class),
                rs.getObject("bucket_end", OffsetDateTime.class),
                Math.max(0L, rs.getLong("play_count")),
                Math.max(0L, rs.getLong("play_seconds")),
                Math.max(0L, rs.getLong("device_count"))
        ));
    }

    @Override
    public List<BucketRow> bucketsForLan(
            UUID userId,
            String lanProgramId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval) {

        MapSqlParameterSource params = baseBucketParams(userId, from, to, tz, truncUnit, stepInterval)
                .addValue("lanProgramId", lanProgramId);

        return jdbcTemplate.query(SQL_BUCKET_LAN, params, (rs, rowNum) -> new BucketRow(
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
}
