package nan.produced.prism.auth.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Shared fields for any authenticated identity stored by the auth service.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractAuthUserEntity {

    /** Internal identifier. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Public subject identifier shared across services. */
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    /** Primary email login. */
    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    /** Optional phone login. */
    @Column(name = "phone", unique = true, columnDefinition = "citext")
    private String phone;

    /** Friendly display name. */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Stored password hash (bcrypt by default). */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Hash algorithm descriptor. */
    @Column(name = "password_algo", length = 16)
    private String passwordAlgo = "bcrypt";

    /** High-level classification of the account. */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 16)
    private UserType userType;

    /** Lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    /** Additional attributes serialized as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Update timestamp. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Default type applied when not provided explicitly. */
    protected abstract UserType defaultUserType();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
        if (this.metadata == null) {
            this.metadata = "{}";
        }
        if (this.userType == null) {
            this.userType = defaultUserType();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.metadata == null) {
            this.metadata = "{}";
        }
    }
}