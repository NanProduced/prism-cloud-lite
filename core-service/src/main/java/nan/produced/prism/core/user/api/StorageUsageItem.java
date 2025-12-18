package nan.produced.prism.core.user.api;

/**
 * 存储使用量条目（按 fileType 聚合）
 */
public record StorageUsageItem(
        StorageFileType fileType,
        int fileCount,
        long totalBytes
) {
}

