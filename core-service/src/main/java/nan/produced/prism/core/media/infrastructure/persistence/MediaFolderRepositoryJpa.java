package nan.produced.prism.core.media.infrastructure.persistence;

import nan.produced.prism.core.media.application.domain.MediaFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 媒体文件夹实体 JPA 操作接口
 * 作为 {@link MediaFolderRepositoryAdapter} 的底层数据访问接口
 *
 * @author Nan
 */
@Repository
public interface MediaFolderRepositoryJpa extends JpaRepository<MediaFolderEntity, String> {

    /**
     * 检查文件夹是否存在且属于指定用户
     *
     * @param folderId 文件夹ID
     * @param userId 用户ID
     * @return 存在返回true
     */
    boolean existsByFolderIdAndUserId(String folderId, UUID userId);

    Optional<MediaFolderEntity> findByFolderIdAndUserId(String folderId, UUID userId);

    List<MediaFolderEntity> findByUserId(UUID userId);

    List<MediaFolderEntity> findByUserIdAndParentFolderId(UUID userId, String parentFolderId);

    List<MediaFolderEntity> findByUserIdAndParentFolderIdIsNull(UUID userId);

    boolean existsByUserIdAndParentFolderId(UUID userId, String parentFolderId);

    boolean existsByUserIdAndParentFolderIdIsNull(UUID userId);

    @Query("""
            SELECT f
            FROM MediaFolderEntity f
            WHERE f.userId = :userId
              AND (f.path = :pathPrefix OR f.path LIKE CONCAT(:pathPrefix, '/%'))
            """)
    List<MediaFolderEntity> findByUserIdAndPathStartingWith(
            @Param("userId") UUID userId,
            @Param("pathPrefix") String pathPrefix);

    long countByUserId(UUID userId);

    @Query("""
            SELECT f.parentFolderId AS parentFolderId, COUNT(f) AS count
            FROM MediaFolderEntity f
            WHERE f.userId = :userId
              AND f.parentFolderId IN :parentFolderIds
            GROUP BY f.parentFolderId
            """)
    List<ChildFolderCountRow> countByUserIdAndParentFolderIdIn(
            @Param("parentFolderIds") List<String> parentFolderIds,
            @Param("userId") UUID userId);

    interface ChildFolderCountRow {
        String getParentFolderId();

        Long getCount();
    }
}
