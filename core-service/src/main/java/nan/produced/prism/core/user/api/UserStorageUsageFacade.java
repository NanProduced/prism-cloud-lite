package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户存储用量变更（跨模块公开 API）。
 * <p>
 * 由 user 模块负责落库与配额冗余字段维护，其他模块（如 media）仅通过此门面触达。
 * <p>
 * 约定：
 * - 增加用量只能调用 {@link #incrementUsage(UUID, StorageSourceType, StorageFileType, int, long)}，参数必须为非负数；
 * - 减少用量只能调用 {@link #decrementUsage(UUID, StorageSourceType, StorageFileType, int, long)}，参数必须为非负数（通常为正数）；
 * - 不应通过传入负数来“反向更新”。实现会对负数做保护（忽略/截断），以避免出现负的 usedBytes。
 */
public interface UserStorageUsageFacade {

    /**
     * 增加用户在指定来源下的存储用量，并同步更新配额冗余字段。
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     * @param fileType   文件类型
     * @param fileCount  文件数量增量（非负数）
     * @param bytes      字节数增量（非负数）
     */
    void incrementUsage(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType,
            int fileCount,
            long bytes);

    /**
     * 减少用户在指定来源下的存储用量，并同步更新配额冗余字段。
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     * @param fileType   文件类型
     * @param fileCount  文件数量减少量（非负数，通常为正数）
     * @param bytes      字节数减少量（非负数，通常为正数）
     */
    void decrementUsage(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType,
            int fileCount,
            long bytes);
}
