package nan.produced.prism.auth.security.principal;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nan.produced.prism.auth.domain.user.UserType;

/**
 * Canonical representation of the claims/header data we stamp onto tokens/gateway headers.
 */
public record AuthClaims(
        String publicId,
        String displayName,
        UserType userType,
        Set<String> roles,
        String subscriptionTier,
        Instant subscriptionExpiresAt) {

    public AuthClaims {
        Objects.requireNonNull(publicId, "publicId is required");
        Objects.requireNonNull(userType, "userType is required");
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
    }

    /**
     * Transforms the data into the CLOUD-AUTH-* header contract described in the OAuth2 plan doc.
     */
    public Map<String, String> toCloudHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("CLOUD-AUTH-ID", publicId);
        if (!roles.isEmpty()) {
            headers.put("CLOUD-AUTH-ROLE", String.join(",", roles));
        }
        if (subscriptionTier != null) {
            headers.put("CLOUD-AUTH-TIER", subscriptionTier);
        }
        if (subscriptionExpiresAt != null) {
            headers.put("CLOUD-AUTH-SUBS-EXPIRE", subscriptionExpiresAt.toString());
        }
        headers.put("CLOUD-AUTH-USER-TYPE", userType.name());
        return headers;
    }

    /**
     * Helper for token customizers to serialize structured claims.
     */
    public Map<String, Object> toTokenClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("public_id", publicId);
        claims.put("user_type", userType.name());
        claims.put("roles", roles);
        if (subscriptionTier != null) {
            claims.put("subscription_tier", subscriptionTier);
        }
        if (subscriptionExpiresAt != null) {
            claims.put("subscription_expires_at", subscriptionExpiresAt.toString());
        }
        return claims;
    }
}