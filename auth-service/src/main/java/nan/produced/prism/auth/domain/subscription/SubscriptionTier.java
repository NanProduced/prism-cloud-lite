package nan.produced.prism.auth.domain.subscription;

import java.util.Locale;

public enum SubscriptionTier {
    FREE,
    PRO;

    public static SubscriptionTier fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return FREE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("PRO".equals(normalized)) {
            return PRO;
        }
        return FREE;
    }
}

