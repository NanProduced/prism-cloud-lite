package nan.produced.prism.core.system.api;

/**
 * 订阅配额（跨模块公开 API）。
 *
 * @param deviceLimit          设备数量上限，-1 表示无限制
 * @param storageLimitBytes    存储上限（字节），-1 表示无限制
 * @param programLimit         节目上限，-1 表示无限制
 * @param programVersionLimit  节目版本上限，-1 表示无限制
 * @param customColumnLimit    自定义列（自定义字段定义）上限，-1 表示无限制
 */
public record SubscriptionQuota(
        Integer deviceLimit,
        Long storageLimitBytes,
        Integer programLimit,
        Integer programVersionLimit,
        Integer customColumnLimit) {
}

