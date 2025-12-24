package nan.produced.prism.core.device.api.event;

import java.time.Instant;
import java.util.UUID;

public record DeviceOnlineSessionReportedEvent(
        UUID userId,
        Long deviceId,
        Instant onlineAt,
        Instant offlineAt,
        String traceId
) {
}

