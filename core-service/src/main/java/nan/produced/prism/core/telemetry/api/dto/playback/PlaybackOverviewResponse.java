package nan.produced.prism.core.telemetry.api.dto.playback;

import java.util.List;

/**
 * 播放统计总览响应（面向首页/概览面板）。
 *
 * @param programTotal 节目维度总计
 * @param mediaTotal 素材维度总计
 * @param topPrograms Top 节目（按时长或次数排序，由服务端决定）
 * @param topMedia Top 素材（按时长或次数排序，由服务端决定）
 */
public record PlaybackOverviewResponse(
        PlaybackTotals programTotal,
        PlaybackTotals mediaTotal,
        List<ProgramPlaySummaryItem> topPrograms,
        List<MediaPlaySummaryItem> topMedia
) {
}

