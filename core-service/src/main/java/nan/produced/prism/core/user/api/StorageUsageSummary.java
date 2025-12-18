package nan.produced.prism.core.user.api;

import java.util.List;

/**
 * 存储使用量汇总
 */
public record StorageUsageSummary(
        long totalBytes,
        List<StorageUsageItem> items
) {
}

