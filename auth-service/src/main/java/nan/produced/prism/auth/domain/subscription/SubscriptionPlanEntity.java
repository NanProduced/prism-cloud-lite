package nan.produced.prism.auth.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Defines the subscription tiers available to the platform.
 */
@Data
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlanEntity {

    /** Plan code (FREE/PRO). */
    @Id
    @Column(name = "code", length = 32)
    private String code;

    /** Human readable plan name. */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** Feature toggles captured as JSON. */
    @Column(name = "features", nullable = false, columnDefinition = "jsonb")
    private String features = "{}";

    /** Whether this plan is assigned by default for new users. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan = false;
}