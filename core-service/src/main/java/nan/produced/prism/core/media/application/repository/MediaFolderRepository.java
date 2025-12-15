package nan.produced.prism.core.media.application.repository;

import java.util.UUID;

public interface MediaFolderRepository {

    /**
     * 检查某个文件夹是否存在且是否属于用户
     * @param folderId 文件夹ID
     * @param userId 用户ID
     * @return 存在返回true
     */
    boolean existsByIdAndUserId(String folderId, UUID userId);
}
