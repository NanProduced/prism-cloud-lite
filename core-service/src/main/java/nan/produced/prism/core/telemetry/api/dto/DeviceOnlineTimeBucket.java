package nan.produced.prism.core.telemetry.api.dto;

import java.time.Instant;

/**
 * 在线时长分桶聚合结果。
 *
 * @param bucketStart 桶起始（UTC Instant）
 * @param bucketEnd   桶结束（UTC Instant）
 * @param bucketSeconds 桶有效时长（秒，已与查询窗口相交；DST/非整点时区会体现为非 86400）
 * @param onlineSeconds 在线时长（秒）
 * @param uptimeRate 在线率（0~1），bucketSeconds=0 时为 0
 */
public record DeviceOnlineTimeBucket(
        Instant bucketStart,
        Instant bucketEnd,
        long bucketSeconds,
        long onlineSeconds,
        double uptimeRate
) {
}

