package nan.produced.prism.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 用户业务侧基本资料
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_user_profile")
public class UserProfileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "subscription_tier", nullable = false, length = 32)
    private String subscriptionTier = "FREE";

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configs", columnDefinition = "jsonb")
    private Map<String, Object> configs = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}