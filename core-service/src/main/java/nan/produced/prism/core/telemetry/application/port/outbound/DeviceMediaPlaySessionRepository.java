package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceMediaPlaySessionRepository {

    record InsertRow(
            UUID userId,
            Long deviceId,
            String mediaId,
            String resOriginName,
            String resMd5Name,
            String itemType,
            boolean lan,
            UUID programId,
            Integer releaseVersion,
            String programVsn,
            String programNameSnapshot,
            String vsnMd5,
            Long vsnSizeBytes,
            String pageName,
            Integer pageIndex,
            String regionName,
            Integer regionIndex,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            Long reportedDuration
    ) {
    }

    record TotalsRow(long playCount, long playSeconds, long deviceCount) {
    }

    record MediaSummaryRow(
            String mediaId,
            String itemType,
            long playCount,
            long playSeconds,
            long deviceCount,
            OffsetDateTime lastPlayedAt
    ) {
    }

    record BucketRow(
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd,
            long playCount,
            long playSeconds,
            long deviceCount
    ) {
    }

    record DeviceSummaryRow(
            Long deviceId,
            long playCount,
            long playSeconds,
            OffsetDateTime lastPlayedAt
    ) {
    }

    record SessionRow(
            Long id,
            Long deviceId,
            String deviceName,
            String mediaId,
            String itemType,
            String resOriginName,
            String resMd5Name,
            boolean lan,
            UUID programId,
            Integer releaseVersion,
            String programVsn,
            String programNameSnapshot,
            String pageName,
            Integer pageIndex,
            String regionName,
            Integer regionIndex,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            OffsetDateTime effectiveStartAt,
            OffsetDateTime effectiveEndAt,
            long playSecondsInRange,
            Long reportedDuration,
            OffsetDateTime createdAt
    ) {
    }

    int insertIgnoreBatch(List<InsertRow> rows);

    TotalsRow totals(UUID userId, OffsetDateTime from, OffsetDateTime to);

    List<MediaSummaryRow> summarize(UUID userId, OffsetDateTime from, OffsetDateTime to, int limit, boolean orderBySeconds);

    List<DeviceSummaryRow> summarizeDevices(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds);

    List<BucketRow> buckets(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);

    List<SessionRow> listSessions(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorStartAt,
            Long cursorId,
            int limit);
}
