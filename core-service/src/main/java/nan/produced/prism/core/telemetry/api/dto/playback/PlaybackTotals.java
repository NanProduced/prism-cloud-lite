package nan.produced.prism.core.telemetry.api.dto.playback;

/**
 * 播放统计总览（范围聚合）。
 *
 * @param playCount 播放次数（按会话计数口径）
 * @param playSeconds 播放总时长（秒，按区间与窗口相交累加）
 * @param deviceCount 有播放行为的设备数（去重）
 */
public record PlaybackTotals(
        long playCount,
        long playSeconds,
        long deviceCount
) {
}

