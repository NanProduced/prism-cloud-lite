package nan.produced.prism.core.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 用户配额使用量统计实体
 * 用于跟踪用户的资源使用情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_user_quota_usage")
public class QuotaUsageEntity {

    /**
     * 配额记录唯一标识符
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 关联的用户ID
     */
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    /**
     * 已使用的存储空间（字节）
     */
    @Column(name = "storage_used_bytes", nullable = false)
    private Long storageUsedBytes = 0L;

    /**
     * 活跃设备数量
     */
    @Column(name = "device_count_active", nullable = false)
    private Integer deviceCountActive = 0;

    /**
     * 活跃程序数量
     */
    @Column(name = "program_count_active", nullable = false)
    private Integer programCountActive = 0;

    /**
     * 版本号，用于乐观锁控制
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    /**
     * 最后更新时间
     */
    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}