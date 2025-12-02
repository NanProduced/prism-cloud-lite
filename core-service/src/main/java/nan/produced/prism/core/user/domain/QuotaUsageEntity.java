package nan.produced.prism.core.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 用户配额使用量统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_user_quota_usage")
public class QuotaUsageEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "storage_used_bytes", nullable = false)
    private Long storageUsedBytes = 0L;

    @Column(name = "device_count_active", nullable = false)
    private Integer deviceCountActive = 0;

    @Column(name = "program_count_active", nullable = false)
    private Integer programCountActive = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}
