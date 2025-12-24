package nan.produced.prism.core.device.api.event;

import java.time.Instant;
import java.util.UUID;

public record DeviceSensorDataReportedEvent(
        UUID userId,
        Long deviceId,
        String sensorData,
        Instant occurredAt,
        String traceId
) {
}

