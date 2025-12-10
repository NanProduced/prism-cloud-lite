package nan.produced.prism.auth.domain.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import nan.produced.prism.auth.domain.user.UserType;

/**
 * Persisted remember-me token used for device level login continuity.
 */
@Getter
@Setter
@Entity
@Table(name = "auth_remember_me_tokens", indexes = {
        @Index(name = "idx_remember_me_user", columnList = "user_id"),
        @Index(name = "idx_remember_me_series", columnList = "series", unique = true)
})
public class RememberMeTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 16)
    private UserType userType;

    @Column(name = "series", nullable = false, length = 64, unique = true)
    private String series;

    @Column(name = "token_hash", nullable = false, length = 96)
    private String tokenHash;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastUsedAt = now;
        if (this.revoked) {
            this.revokedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        if (!this.revoked) {
            this.revokedAt = null;
        }
    }

    public boolean isExpired(Instant reference) {
        return reference.isAfter(this.expiresAt);
    }
}
