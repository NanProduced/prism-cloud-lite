package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthInternalUserSearchItem(
    String publicId,
    String userId,
    String email,
    String phone,
    String status,
    Instant createdAt
) {
}

