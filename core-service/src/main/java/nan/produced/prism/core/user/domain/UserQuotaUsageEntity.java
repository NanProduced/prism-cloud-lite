package nan.produced.prism.core.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户资源配额使用情况
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_user_quota_usage")
public class UserQuotaUsageEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    /**
     * 上云设备数量
     */
    @Column(name = "device_count", nullable = false)
    @Builder.Default
    private Integer deviceCount = 0;

    /**
     * 创建节目数量
     */
    @Column(name = "program_count", nullable = false)
    @Builder.Default
    private Integer programCount = 0;

    /**
     * 自定义列配置数量
     */
    @Column(name = "custom_column_count", nullable = false)
    @Builder.Default
    private Integer customColumnCount = 0;

    /**
     * 存储总量（冗余字段，用于快速配额检查）
     * 等于 user_storage_usage 表中该用户所有记录的 total_bytes 之和
     */
    @Column(name = "storage_total_bytes", nullable = false)
    @Builder.Default
    private Long storageTotalBytes = 0L;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;
}
