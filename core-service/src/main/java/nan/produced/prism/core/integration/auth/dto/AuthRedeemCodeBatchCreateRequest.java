package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthRedeemCodeBatchCreateRequest(
        String tier,
        Integer durationDays,
        Integer count,
        Instant expiresAt
) {
}

