package nan.produced.prism.core.media.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import org.springframework.stereotype.Component;

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
}
