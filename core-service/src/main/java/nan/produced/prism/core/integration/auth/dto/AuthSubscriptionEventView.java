package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthSubscriptionEventView(
    Long id,
    String type,
    boolean success,
    String code,
    String metadata,
    Instant createdAt
) {
}

