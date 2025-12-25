package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthSubscriptionView(
    String tier,
    Instant startAt,
    Instant endAt,
    boolean proActive
) {
}

