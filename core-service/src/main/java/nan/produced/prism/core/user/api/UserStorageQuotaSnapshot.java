package nan.produced.prism.core.user.api;

import java.time.Instant;

/**
 * 面向跨模块的「存储配额快照」。
 *
 * <p>避免直接暴露 user.dto 下的视图对象，保持模块边界清晰。</p>
 */
public record UserStorageQuotaSnapshot(
        String tier,
        Long quotaBytes,
        long usedBytes,
        Long availableBytes,
        Double percent,
        Instant usageUpdatedAt
) {
}
