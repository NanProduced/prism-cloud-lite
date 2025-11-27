package nan.produced.prism.auth.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;
import nan.produced.prism.auth.domain.user.EndUserEntity;

/**
 * Tracks the active subscription of a user.
 */
@Data
@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptionEntity {

    /** Surrogate key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user (one subscription per user in personal edition). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private EndUserEntity user;

    /** Plan that the user purchased. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_code", referencedColumnName = "code", nullable = false)
    private SubscriptionPlanEntity plan;

    /** Lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** When the subscription became active. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    /** Expiration time for entitlement checks. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}