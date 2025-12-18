package nan.produced.prism.core.media.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.domain.MediaFolderEntity;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 媒体文件夹数据仓库适配器
 * 实现 {@link MediaFolderRepository} 出站端口
 * 作为六边形架构中的适配器，负责将域模型的仓库接口与底层的持久化实现（Spring Data JPA）相连接
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaFolderRepositoryAdapter implements MediaFolderRepository {

    /**
     * Spring Data JPA 仓库，用于媒体文件夹数据的 CRUD 操作
     */
    private final MediaFolderRepositoryJpa mediaFolderRepositoryJpa;

    /**
     * 检查某个文件夹是否存在且是否属于用户
     *
     * @param folderId 文件夹ID
     * @param userId   用户ID
     * @return 存在返回true
     */
    @Override
    public boolean existsByIdAndUserId(String folderId, UUID userId) {
        if (folderId == null || userId == null) {
            log.warn("MediaFolderRepositoryAdapter - 检查文件夹存在性时参数为空: folderId={}, userId={}", folderId, userId);
            return false;
        }
        return mediaFolderRepositoryJpa.existsByFolderIdAndUserId(folderId, userId);
    }

    @Override
    public Optional<MediaFolderEntity> findByIdAndUserId(String folderId, UUID userId) {
        if (!StringUtils.hasText(folderId) || userId == null) {
            return Optional.empty();
        }
        return mediaFolderRepositoryJpa.findByFolderIdAndUserId(folderId, userId);
    }

    @Override
    public List<MediaFolderEntity> findAllByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return mediaFolderRepositoryJpa.findByUserId(userId);
    }

    @Override
    public List<MediaFolderEntity> findByUserIdAndParentFolderId(UUID userId, String parentFolderId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(parentFolderId)) {
            return mediaFolderRepositoryJpa.findByUserIdAndParentFolderIdIsNull(userId);
        }
        return mediaFolderRepositoryJpa.findByUserIdAndParentFolderId(userId, parentFolderId);
    }

    @Override
    public MediaFolderEntity save(MediaFolderEntity folder) {
        if (folder == null) {
            throw new IllegalArgumentException("MediaFolderEntity cannot be null");
        }
        return mediaFolderRepositoryJpa.save(folder);
    }

    @Override
    public List<MediaFolderEntity> saveAll(List<MediaFolderEntity> folders) {
        if (folders == null || folders.isEmpty()) {
            return Collections.emptyList();
        }
        return mediaFolderRepositoryJpa.saveAll(folders);
    }

    @Override
    public void delete(MediaFolderEntity folder) {
        if (folder == null) {
            return;
        }
        mediaFolderRepositoryJpa.delete(folder);
    }

    @Override
    public boolean existsByUserIdAndParentFolderId(UUID userId, String parentFolderId) {
        if (userId == null) {
            return false;
        }
        if (!StringUtils.hasText(parentFolderId)) {
            return mediaFolderRepositoryJpa.existsByUserIdAndParentFolderIdIsNull(userId);
        }
        return mediaFolderRepositoryJpa.existsByUserIdAndParentFolderId(userId, parentFolderId);
    }

    @Override
    public List<MediaFolderEntity> findByUserIdAndPathStartingWith(UUID userId, String pathPrefix) {
        if (userId == null || !StringUtils.hasText(pathPrefix)) {
            return Collections.emptyList();
        }
        return mediaFolderRepositoryJpa.findByUserIdAndPathStartingWith(userId, pathPrefix);
    }

    @Override
    public Map<String, Long> countChildFoldersByParentIds(UUID userId, List<String> parentFolderIds) {
        if (userId == null || parentFolderIds == null || parentFolderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        var validParentIds = parentFolderIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (validParentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        var counts = new HashMap<String, Long>();
        for (var row : mediaFolderRepositoryJpa.countByUserIdAndParentFolderIdIn(validParentIds, userId)) {
            counts.put(row.getParentFolderId(), row.getCount());
        }
        return counts;
    }

    @Override
    public long countByUserId(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        return mediaFolderRepositoryJpa.countByUserId(userId);
    }
}
