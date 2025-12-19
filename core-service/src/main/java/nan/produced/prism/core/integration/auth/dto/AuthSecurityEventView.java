package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthSecurityEventView(
    Long id,
    String type,
    boolean success,
    String ipAddress,
    String deviceName,
    String userAgent,
    String metadata,
    Instant createdAt
) {
}

