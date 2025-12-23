package nan.produced.prism.core.telemetry.api.dto;

import java.time.Instant;

/**
 * 活跃设备数分桶聚合结果。
 *
 * @param bucketStart 桶起始（UTC Instant）
 * @param bucketEnd 桶结束（UTC Instant）
 * @param bucketSeconds 桶有效时长（秒）
 * @param activeDevices 桶内活跃设备数（有任意在线即视为活跃）
 */
public record ActiveDeviceCountBucket(
        Instant bucketStart,
        Instant bucketEnd,
        long bucketSeconds,
        long activeDevices
) {
}

