package nan.produced.prism.core.media.infrastructure.persistence;

import nan.produced.prism.core.media.application.domain.MediaFolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
