package nan.produced.prism.core.telemetry.api.dto;

import java.time.Instant;

/**
 * 并发在线分桶聚合结果。
 *
 * @param bucketStart 桶起始（UTC Instant）
 * @param bucketEnd 桶结束（UTC Instant）
 * @param bucketSeconds 桶有效时长（秒）
 * @param totalOnlineDeviceSeconds 桶内“设备在线秒数”总和（=各设备在线时长累加）
 * @param avgConcurrent 平均并发（totalOnlineDeviceSeconds / bucketSeconds）
 * @param maxConcurrent 最大并发（桶内峰值）
 */
public record DeviceConcurrencyBucket(
        Instant bucketStart,
        Instant bucketEnd,
        long bucketSeconds,
        long totalOnlineDeviceSeconds,
        double avgConcurrent,
        long maxConcurrent
) {
}

