package nan.produced.prism.core.media.infrastructure.persistence;

import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * 检查素材是否存在且属于用户
     */
    boolean existsByIdAndUserId(String id, UUID userId);
}
