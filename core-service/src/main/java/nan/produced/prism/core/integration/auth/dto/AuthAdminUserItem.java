package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

public record AuthAdminUserItem(
    String publicId,
    String userId,
    String username,
    String userType,
    String status,
    Instant createdAt
) {
}

