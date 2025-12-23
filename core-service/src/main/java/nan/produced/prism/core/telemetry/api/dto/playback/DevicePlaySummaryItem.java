package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.Instant;

/**
 * 设备播放汇总项（按设备聚合）。
 *
 * @param deviceId 设备ID
 * @param playCount 播放次数
 * @param playSeconds 播放时长（秒）
 * @param lastPlayedAt 窗口内最后一次播放时间（UTC，可为空）
 */
public record DevicePlaySummaryItem(
        Long deviceId,
        long playCount,
        long playSeconds,
        Instant lastPlayedAt
) {
}

