package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceProgramPlaySessionRepository {

    record InsertRow(
            UUID userId,
            Long deviceId,
            boolean lan,
            String lanProgramId,
            UUID programId,
            Integer releaseVersion,
            String programVsn,
            String programNameSnapshot,
            String vsnMd5,
            Long vsnSizeBytes,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
    }

    record TotalsRow(long playCount, long playSeconds, long deviceCount) {
    }

    record ProgramSummaryRow(
            boolean lan,
            UUID programId,
            Integer releaseVersion,
            String lanProgramId,
            String programNameSnapshot,
            String programVsn,
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

    int insertIgnoreBatch(List<InsertRow> rows);

    TotalsRow totals(UUID userId, OffsetDateTime from, OffsetDateTime to);

    List<ProgramSummaryRow> summarize(UUID userId, OffsetDateTime from, OffsetDateTime to, int limit, boolean orderBySeconds);

    List<DeviceSummaryRow> summarizeDevicesForPlatform(
            UUID userId,
            UUID programId,
            int releaseVersion,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds);

    List<DeviceSummaryRow> summarizeDevicesForLan(
            UUID userId,
            String lanProgramId,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit,
            boolean orderBySeconds);

    List<BucketRow> bucketsForPlatform(
            UUID userId,
            UUID programId,
            int releaseVersion,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);

    List<BucketRow> bucketsForLan(
            UUID userId,
            String lanProgramId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);
}

