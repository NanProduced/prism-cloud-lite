package nan.produced.prism.core.media.application.repository;

import nan.produced.prism.core.media.application.domain.MediaAssetEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 媒体素材仓库端口
 * <p>
 * 六边形架构中的出站端口，定义媒体素材的数据访问接口
 *
 * @author Nan
 */
public interface MediaAssetRepository {

    /**
     * 根据 groupId 查找素材（幂等检查）
     *
     * @param groupId 素材组ID
     * @return 素材实体，不存在返回空
     */
    Optional<MediaAssetEntity> findByGroupId(String groupId);

    /**
     * 批量根据 groupId 查找素材
     *
     * @param groupIds 素材组ID列表
     * @return 已存在的素材列表
     */
    List<MediaAssetEntity> findByGroupIdIn(List<String> groupIds);

    /**
     * 根据用户ID和文件夹ID查找素材列表
     *
     * @param userId   用户ID
     * @param folderId 文件夹ID（null表示根目录）
     * @return 素材列表
     */
    List<MediaAssetEntity> findByUserIdAndFolderId(UUID userId, String folderId);

    /**
     * 根据用户ID和文件夹ID查找素材列表（联表加载 original/cover 文件信息）
     */
    List<MediaAssetEntity> findWithFilesByUserIdAndFolderId(UUID userId, String folderId);

    /**
     * 根据ID查找素材
     *
     * @param id 素材ID
     * @return 素材实体，不存在返回空
     */
    Optional<MediaAssetEntity> findById(String id);

    /**
     * 批量根据 ID 查找素材
     */
    List<MediaAssetEntity> findAllById(Iterable<String> ids);

    /**
     * 根据 ID 查找素材（联表加载 original/cover 文件信息）
     */
    Optional<MediaAssetEntity> findWithFilesById(String id);

    /**
     * 根据 ID 查找素材（需校验归属，联表加载 original/cover 文件信息）
     */
    Optional<MediaAssetEntity> findWithFilesByIdAndUserId(String id, UUID userId);

    /**
     * 保存素材
     *
     * @param asset 素材实体
     * @return 保存后的素材实体
     */
    MediaAssetEntity save(MediaAssetEntity asset);

    /**
     * 批量保存素材
     *
     * @param assets 素材实体列表
     * @return 保存后的素材实体列表
     */
    List<MediaAssetEntity> saveAll(List<MediaAssetEntity> assets);

    /**
     * 删除素材
     */
    void delete(MediaAssetEntity asset);

    /**
     * 检查素材是否存在且属于用户
     *
     * @param id     素材ID
     * @param userId 用户ID
     * @return 存在返回true
     */
    boolean existsByIdAndUserId(String id, UUID userId);

    /**
     * 检查文件夹下是否存在素材
     */
    boolean existsByUserIdAndFolderId(UUID userId, String folderId);

    /**
     * 按 folderId 统计素材数量（group by folderId）
     */
    Map<String, Long> countAssetsByFolderIds(UUID userId, List<String> folderIds);
}
