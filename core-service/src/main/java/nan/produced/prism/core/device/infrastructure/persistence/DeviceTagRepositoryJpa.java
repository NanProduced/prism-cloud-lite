package nan.produced.prism.core.device.infrastructure.persistence;

import nan.produced.prism.core.device.domain.tags.DeviceTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设备标签 JPA 仓库
 *
 * @author Nan
 */
@Repository
public interface DeviceTagRepositoryJpa extends JpaRepository<DeviceTagEntity, Long> {

    /**
     * 根据用户ID和Slug查找标签
     */
    Optional<DeviceTagEntity> findByUserIdAndSlug(UUID userId, String slug);

    /**
     * 检查用户是否存在指定Slug的标签
     */
    boolean existsByUserIdAndSlug(UUID userId, String slug);

    /**
     * 根据用户ID查找所有标签
     */
    List<DeviceTagEntity> findByUserIdOrderByCreateTimeDesc(UUID userId);

    /**
     * 根据用户ID和标签ID查找标签
     */
    Optional<DeviceTagEntity> findByTagIdAndUserId(Long tagId, UUID userId);

    /**
     * 根据用户ID和Slug列表批量查找标签
     */
    List<DeviceTagEntity> findByUserIdAndSlugIn(UUID userId, List<String> slugs);

    /**
     * 删除用户的指定标签
     */
    void deleteByUserIdAndSlug(UUID userId, String slug);
}
