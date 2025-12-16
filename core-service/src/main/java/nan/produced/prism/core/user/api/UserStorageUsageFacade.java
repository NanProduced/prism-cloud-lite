package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户存储用量变更（跨模块公开 API）。
 * <p>
 * 由 user 模块负责落库与配额冗余字段维护，其他模块（如 media）仅通过此门面触达。
 */
public interface UserStorageUsageFacade {

    /**
     * 增加用户在指定来源下的存储用量，并同步更新配额冗余字段。
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     * @param fileType   文件类型
     * @param fileCount  文件数量增量
     * @param bytes      字节数增量
     */
    void incrementUsage(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType,
            int fileCount,
            long bytes);
}
