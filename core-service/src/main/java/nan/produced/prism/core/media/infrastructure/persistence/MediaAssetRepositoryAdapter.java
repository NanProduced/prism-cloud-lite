package nan.produced.prism.core.media.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 媒体素材数据仓库适配器
 * <p>
 * 实现 {@link MediaAssetRepository} 出站端口
 * 作为六边形架构中的适配器，负责将域模型的仓库接口与底层的持久化实现（Spring Data JPA）相连接
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAssetRepositoryAdapter implements MediaAssetRepository {

    private final MediaAssetRepositoryJpa mediaAssetRepositoryJpa;

    @Override
    public Optional<MediaAssetEntity> findByGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            log.debug("MediaAssetRepositoryAdapter - groupId为空，返回空结果");
            return Optional.empty();
        }
        return mediaAssetRepositoryJpa.findByGroupId(groupId);
    }

    @Override
    public List<MediaAssetEntity> findByGroupIdIn(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            log.debug("MediaAssetRepositoryAdapter - groupIds列表为空，返回空结果");
            return Collections.emptyList();
        }
        var validGroupIds = groupIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (validGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mediaAssetRepositoryJpa.findByGroupIdIn(validGroupIds);
    }

    @Override
    public List<MediaAssetEntity> findByUserIdAndFolderId(UUID userId, String folderId) {
        if (userId == null) {
            log.warn("MediaAssetRepositoryAdapter - userId为空，返回空结果");
            return Collections.emptyList();
        }
        return mediaAssetRepositoryJpa.findByUserIdAndFolderId(userId, folderId);
    }

    @Override
    public Optional<MediaAssetEntity> findById(String id) {
        if (id == null || id.isBlank()) {
            log.debug("MediaAssetRepositoryAdapter - id为空，返回空结果");
            return Optional.empty();
        }
        return mediaAssetRepositoryJpa.findById(id);
    }

    @Override
    public MediaAssetEntity save(MediaAssetEntity asset) {
        if (asset == null) {
            throw new IllegalArgumentException("MediaAssetEntity cannot be null");
        }
        return mediaAssetRepositoryJpa.save(asset);
    }

    @Override
    public List<MediaAssetEntity> saveAll(List<MediaAssetEntity> assets) {
        if (assets == null || assets.isEmpty()) {
            return Collections.emptyList();
        }
        return mediaAssetRepositoryJpa.saveAll(assets);
    }

    @Override
    public boolean existsByIdAndUserId(String id, UUID userId) {
        if (id == null || id.isBlank() || userId == null) {
            log.warn("MediaAssetRepositoryAdapter - 检查素材存在性时参数为空: id={}, userId={}", id, userId);
            return false;
        }
        return mediaAssetRepositoryJpa.existsByIdAndUserId(id, userId);
    }
}
