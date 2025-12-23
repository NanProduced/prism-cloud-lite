package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.Instant;

/**
 * 素材播放汇总项（按素材聚合）。
 *
 * @param mediaId 平台侧素材ID（MediaAsset.id）
 * @param mediaTitle 素材展示名称（可为空，若可关联 media_asset）
 * @param itemType 素材类型（设备上报，可为空）
 * @param playCount 播放次数
 * @param playSeconds 播放时长（秒）
 * @param deviceCount 播放设备数（去重）
 * @param lastPlayedAt 窗口内最后一次播放时间（UTC，可为空）
 */
public record MediaPlaySummaryItem(
        String mediaId,
        String mediaTitle,
        String itemType,
        long playCount,
        long playSeconds,
        long deviceCount,
        Instant lastPlayedAt
) {
}
