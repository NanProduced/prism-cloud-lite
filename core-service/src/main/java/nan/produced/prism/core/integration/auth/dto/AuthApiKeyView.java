package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthApiKeyView(
    String id,
    String name,
    String clientId,
    String clientSecret,
    Instant createdAt,
    Instant lastUsedAt
) {
}

