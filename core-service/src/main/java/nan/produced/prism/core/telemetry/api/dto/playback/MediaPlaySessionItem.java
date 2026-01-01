package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaPlaySessionItem(
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

