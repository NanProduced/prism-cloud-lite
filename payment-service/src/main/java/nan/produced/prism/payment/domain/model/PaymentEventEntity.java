package nan.produced.prism.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "pca_payment_events", indexes = {
    @Index(name = "idx_pca_payment_events_user_id", columnList = "user_id"),
    @Index(name = "idx_pca_payment_events_created_at", columnList = "created_at"),
    @Index(name = "idx_pca_payment_events_order_id", columnList = "order_id"),
    @Index(name = "idx_pca_payment_events_subscription_id", columnList = "subscription_id")
})
public class PaymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private PaymentEventType eventType;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.metadata == null) {
            this.metadata = "{}";
        }
    }
}
