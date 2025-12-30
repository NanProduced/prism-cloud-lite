package nan.produced.prism.core.telemetry.api.dto.playback;

import java.time.Instant;
import java.util.UUID;
import nan.produced.prism.core.telemetry.api.dto.ResourceStatus;

/**
 * 节目播放汇总项（按节目聚合）。
 *
 * @param lan 是否 LAN 节目（LAN 节目无法与平台 Program/Release 建立关联）
 * @param programId 平台侧 Program.id（LAN 节目为空）
 * @param releaseVersion 平台侧发布版本号（LAN 节目为空）
 * @param lanProgramId LAN 节目 ID（平台节目为空）
 * @param programName 节目展示名称（快照/解析结果，可为空）
 * @param playCount 播放次数
 * @param playSeconds 播放时长（秒）
 * @param deviceCount 播放设备数（去重）
 * @param lastPlayedAt 窗口内最后一次播放时间（UTC，可为空）
 */
public record ProgramPlaySummaryItem(
        boolean lan,
        UUID programId,
        Integer releaseVersion,
        String lanProgramId,
        String programName,
        long playCount,
        long playSeconds,
        long deviceCount,
        Instant lastPlayedAt,
        ResourceStatus status,
        Instant deletedAt
) {
}
