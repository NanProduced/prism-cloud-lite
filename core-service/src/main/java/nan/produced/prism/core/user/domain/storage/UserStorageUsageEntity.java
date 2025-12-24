package nan.produced.prism.core.user.domain.storage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户存储使用情况
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pcc_user_storage_usage")
public class UserStorageUsageEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * 文件来源
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private StorageSourceType sourceType;

    /**
     * 文件类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private StorageFileType fileType;

    /**
     * 文件数量
     */
    @Column(name = "file_count", nullable = false)
    @Builder.Default
    private Integer fileCount = 0;

    /**
     * 文件总大小
     */
    @Column(name = "total_bytes", nullable = false)
    @Builder.Default
    private Long totalBytes = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
