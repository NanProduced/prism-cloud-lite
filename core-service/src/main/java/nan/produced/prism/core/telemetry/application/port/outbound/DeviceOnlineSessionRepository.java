package nan.produced.prism.core.telemetry.application.port.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceOnlineSessionRepository {

    record OnlineSecondsByDeviceRow(Long deviceId, long onlineSeconds) {
    }

    record OnlineSecondsBucketRow(OffsetDateTime bucketStart, OffsetDateTime bucketEnd, long bucketSeconds, long onlineSeconds) {
    }

    record ActiveDeviceCountBucketRow(OffsetDateTime bucketStart, OffsetDateTime bucketEnd, long bucketSeconds, long activeDevices) {
    }

    record ConcurrencyBucketRow(
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd,
            long bucketSeconds,
            long totalOnlineDeviceSeconds,
            long maxConcurrent
    ) {
    }

    record SessionStatsRow(
            long sessionCount,
            long totalOnlineSeconds,
            double avgSessionSeconds,
            long maxSessionSeconds,
            long p95SessionSeconds
    ) {
    }

    record OfflineGapStatsRow(
            long gapCount,
            long totalGapSeconds,
            double avgGapSeconds,
            long maxGapSeconds
    ) {
    }

    record SessionItemRow(
            long sessionId,
            OffsetDateTime onlineAt,
            OffsetDateTime offlineAt,
            OffsetDateTime effectiveOnlineAt,
            OffsetDateTime effectiveOfflineAt,
            long onlineSecondsInRange
    ) {
    }

    boolean insertIgnore(UUID userId, Long deviceId, OffsetDateTime onlineAt, OffsetDateTime offlineAt);

    List<OnlineSecondsByDeviceRow> sumOnlineSecondsByDevice(UUID userId, OffsetDateTime from, OffsetDateTime to);

    List<OnlineSecondsBucketRow> sumOnlineSecondsByBucketForDevice(
            UUID userId,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);

    List<ActiveDeviceCountBucketRow> countActiveDevicesByBucket(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);

    List<ConcurrencyBucketRow> concurrencyByBucket(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            String tz,
            String truncUnit,
            String stepInterval);

    SessionStatsRow sessionStatsForDevice(UUID userId, Long deviceId, OffsetDateTime from, OffsetDateTime to);

    OfflineGapStatsRow offlineGapStatsForDevice(UUID userId, Long deviceId, OffsetDateTime from, OffsetDateTime to);

    List<SessionItemRow> listSessionsForDevice(
            UUID userId,
            Long deviceId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursor,
            int limit);
}

