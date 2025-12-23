package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.Instant;

/**
 * 播放统计分桶结果。
 *
 * @param bucketStart 桶起始（UTC Instant）
 * @param bucketEnd   桶结束（UTC Instant）
 * @param playCount   桶内播放次数（按会话计数口径）
 * @param playSeconds 桶内播放时长（秒，按区间与桶相交累加）
 * @param deviceCount 桶内有播放行为的设备数（去重）
 */
public record PlaybackBucket(
        Instant bucketStart,
        Instant bucketEnd,
        long playCount,
        long playSeconds,
        long deviceCount
) {
}

