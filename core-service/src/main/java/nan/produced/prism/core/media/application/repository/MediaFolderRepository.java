package nan.produced.prism.core.media.application.repository;

import nan.produced.prism.core.media.application.domain.MediaFolderEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MediaFolderRepository {

    /**
     * 检查某个文件夹是否存在且是否属于用户
     * @param folderId 文件夹ID
     * @param userId 用户ID
     * @return 存在返回true
     */
    boolean existsByIdAndUserId(String folderId, UUID userId);

    /**
     * 查询文件夹（需校验归属）
     */
    Optional<MediaFolderEntity> findByIdAndUserId(String folderId, UUID userId);

    /**
     * 查询用户所有文件夹
     */
    List<MediaFolderEntity> findAllByUserId(UUID userId);

    /**
     * 查询指定父目录下的子文件夹（null = 根目录）
     */
    List<MediaFolderEntity> findByUserIdAndParentFolderId(UUID userId, String parentFolderId);

    /**
     * 保存文件夹
     */
    MediaFolderEntity save(MediaFolderEntity folder);

    /**
     * 批量保存文件夹
     */
    List<MediaFolderEntity> saveAll(List<MediaFolderEntity> folders);

    /**
     * 删除文件夹
     */
    void delete(MediaFolderEntity folder);

    /**
     * 检查是否存在子文件夹
     */
    boolean existsByUserIdAndParentFolderId(UUID userId, String parentFolderId);

    /**
     * 查询以某个 path 为前缀的文件夹（用于移动时更新子树）
     */
    List<MediaFolderEntity> findByUserIdAndPathStartingWith(UUID userId, String pathPrefix);

    /**
     * 统计指定父文件夹下的子文件夹数量（group by parentFolderId）
     */
    Map<String, Long> countChildFoldersByParentIds(UUID userId, List<String> parentFolderIds);

    /**
     * 统计用户文件夹数量
     */
    long countByUserId(UUID userId);
}
