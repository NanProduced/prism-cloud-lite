package nan.produced.prism.auth.domain.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * API Key metadata for end-user programmatic access (client_credentials).
 * <p>
 * The actual client secret is stored in {@code oauth2_registered_client} (hashed).
 * This table keeps a user-friendly index for listing/managing API keys.
 * </p>
 */
@Getter
@Setter
@Entity
@Table(name = "auth_api_keys", indexes = {
    @Index(name = "idx_api_keys_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_api_keys_client_id", columnList = "client_id", unique = true)
})
public class ApiKeyEntity {

    /**
     * Same value as {@code oauth2_registered_client.id}.
     */
    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "client_id", nullable = false, length = 100, unique = true)
    private String clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

