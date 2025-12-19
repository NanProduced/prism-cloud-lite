package nan.produced.prism.core.integration.auth.dto;

import java.time.Instant;

/**
 * Auth-service internal response for remember-me devices.
 * <p>
 * Field names align with auth-service {@code RememberMeTokenService.RememberedDeviceView}.
 * </p>
 */
public record AuthRememberedDeviceView(
    String series,
    String deviceName,
    String ipAddress,
    String userAgent,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt,
    boolean current
) {}

