package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProgramPlaySessionItem(
        Long id,
        Long deviceId,
        String deviceName,
        boolean lan,
        String lanProgramId,
        UUID programId,
        Integer releaseVersion,
        String programVsn,
        String programNameSnapshot,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime effectiveStartAt,
        OffsetDateTime effectiveEndAt,
        long playSecondsInRange,
        OffsetDateTime createdAt
) {
}

