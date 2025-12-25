package nan.produced.prism.auth.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pca_redeem_codes", indexes = {
    @Index(name = "idx_pca_redeem_codes_enabled_expire", columnList = "enabled, expires_at"),
    @Index(name = "idx_pca_redeem_codes_redeemed_by", columnList = "redeemed_by, redeemed_at")
})
public class RedeemCodeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 16)
    private SubscriptionTier tier = SubscriptionTier.PRO;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "redeemed_by")
    private UUID redeemedBy;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
    }
}

