package nan.produced.prism.core.user.repository;

import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * 用户存储使用情况仓库
 *
 * @author Nan
 */
@Repository
public interface UserStorageUsageRepository extends JpaRepository<UserStorageUsageEntity, UUID> {

    /**
     * 根据用户ID、来源类型和文件类型查找存储记录
     */
    Optional<UserStorageUsageEntity> findByUserIdAndSourceTypeAndFileType(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType);

    List<UserStorageUsageEntity> findByUserIdAndSourceType(UUID userId, StorageSourceType sourceType);

    /**
     * 增加存储使用量（原子操作）
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     * @param fileType   文件类型
     * @param fileCount  文件数量增量
     * @param bytes      字节数增量
     * @return 更新的行数
     */
    @Modifying
    @Query("""
            UPDATE UserStorageUsageEntity u
            SET u.fileCount = CASE WHEN (u.fileCount + :fileCount) < 0 THEN 0 ELSE (u.fileCount + :fileCount) END,
                u.totalBytes = CASE WHEN (u.totalBytes + :bytes) < 0 THEN 0 ELSE (u.totalBytes + :bytes) END
            WHERE u.userId = :userId
              AND u.sourceType = :sourceType
              AND u.fileType = :fileType
            """)
    int incrementUsage(
            @Param("userId") UUID userId,
            @Param("sourceType") StorageSourceType sourceType,
            @Param("fileType") StorageFileType fileType,
            @Param("fileCount") int fileCount,
            @Param("bytes") long bytes);

    /**
     * 减少存储使用量（原子操作，数值下限为 0）
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     * @param fileType   文件类型
     * @param fileCount  文件数量减少量（正数）
     * @param bytes      字节数减少量（正数）
     * @return 更新的行数
     */
    @Modifying
    @Query("""
            UPDATE UserStorageUsageEntity u
            SET u.fileCount = CASE WHEN (u.fileCount - :fileCount) < 0 THEN 0 ELSE (u.fileCount - :fileCount) END,
                u.totalBytes = CASE WHEN (u.totalBytes - :bytes) < 0 THEN 0 ELSE (u.totalBytes - :bytes) END
            WHERE u.userId = :userId
              AND u.sourceType = :sourceType
              AND u.fileType = :fileType
            """)
    int decrementUsage(
            @Param("userId") UUID userId,
            @Param("sourceType") StorageSourceType sourceType,
            @Param("fileType") StorageFileType fileType,
            @Param("fileCount") int fileCount,
            @Param("bytes") long bytes);
}
