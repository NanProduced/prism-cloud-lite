package nan.produced.prism.core.media.infrastructure.persistence;

import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 媒体素材 JPA 仓库
 *
 * @author Nan
 */
@Repository
public interface MediaAssetRepositoryJpa extends JpaRepository<MediaAssetEntity, String> {

    /**
     * 根据 groupId 查找素材（幂等检查）
     */
    Optional<MediaAssetEntity> findByGroupId(String groupId);

    /**
     * 批量根据 groupId 查找素材
     */
    List<MediaAssetEntity> findByGroupIdIn(List<String> groupIds);

    /**
     * 根据用户ID和文件夹ID查找素材列表
     */
    List<MediaAssetEntity> findByUserIdAndFolderId(UUID userId, String folderId);

    List<MediaAssetEntity> findByUserIdAndFolderIdIsNull(UUID userId);

    @Query("""
            SELECT a
            FROM MediaAssetEntity a
            JOIN FETCH a.originalFile
            LEFT JOIN FETCH a.coverFile
            WHERE a.userId = :userId
              AND a.folderId = :folderId
            """)
    List<MediaAssetEntity> findWithFilesByUserIdAndFolderId(
            @Param("userId") UUID userId,
            @Param("folderId") String folderId);

    @Query("""
            SELECT a
            FROM MediaAssetEntity a
            JOIN FETCH a.originalFile
            LEFT JOIN FETCH a.coverFile
            WHERE a.userId = :userId
              AND a.folderId IS NULL
            """)
    List<MediaAssetEntity> findWithFilesByUserIdAndFolderIdIsNull(@Param("userId") UUID userId);

    @Query("""
            SELECT a
            FROM MediaAssetEntity a
            JOIN FETCH a.originalFile
            LEFT JOIN FETCH a.coverFile
            WHERE a.id = :id
            """)
    Optional<MediaAssetEntity> findWithFilesById(@Param("id") String id);

    @Query("""
            SELECT a
            FROM MediaAssetEntity a
            JOIN FETCH a.originalFile
            LEFT JOIN FETCH a.coverFile
            WHERE a.id = :id
              AND a.userId = :userId
            """)
    Optional<MediaAssetEntity> findWithFilesByIdAndUserId(
            @Param("id") String id,
            @Param("userId") UUID userId);

    /**
     * 检查素材是否存在且属于用户
     */
    boolean existsByIdAndUserId(String id, UUID userId);

    boolean existsByUserIdAndFolderId(UUID userId, String folderId);

    boolean existsByUserIdAndFolderIdIsNull(UUID userId);

    @Query("""
            SELECT a.folderId AS folderId, COUNT(a) AS count
            FROM MediaAssetEntity a
            WHERE a.userId = :userId
              AND a.folderId IN :folderIds
            GROUP BY a.folderId
            """)
    List<FolderAssetCountRow> countByUserIdAndFolderIdIn(
            @Param("folderIds") List<String> folderIds,
            @Param("userId") UUID userId);

    interface FolderAssetCountRow {
        String getFolderId();

        Long getCount();
    }
}
