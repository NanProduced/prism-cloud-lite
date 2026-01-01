package nan.produced.prism.core.telemetry.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.device.domain.report.media.MediaPlayTimesReport;
import nan.produced.prism.core.device.domain.report.program.ProgramPlayTimesReport;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;
import nan.produced.prism.core.telemetry.api.dto.playback.DevicePlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySessionItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackBucket;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackOverviewResponse;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackSort;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySessionItem;

/**
 * 播放统计（节目/素材）Telemetry 能力。
 *
 * <p>写入：device-service 通过 core-service 接收设备上报的播放记录后落库。</p>
 * <p>查询：面向 SPA 的范围聚合/分桶聚合/设备分布等基础统计。</p>
 */
public interface PlaybackTelemetryFacade {

    void recordProgramPlayRecords(UUID userId, Long deviceId, List<ProgramPlayTimesReport> reports, String traceId);

    void recordMediaPlayRecords(UUID userId, Long deviceId, List<MediaPlayTimesReport> reports, String traceId);

    PlaybackOverviewResponse getOverview(UUID userId, Instant from, Instant to, Integer top, PlaybackSort sort);

    List<ProgramPlaySummaryItem> summarizePrograms(UUID userId, Instant from, Instant to, Integer limit, PlaybackSort sort);

    List<PlaybackBucket> getProgramBuckets(
            UUID userId,
            UUID programId,
            Integer releaseVersion,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    List<DevicePlaySummaryItem> summarizeProgramDevices(
            UUID userId,
            UUID programId,
            Integer releaseVersion,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort);

    List<PlaybackBucket> getLanProgramBuckets(
            UUID userId,
            String lanProgramId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    List<DevicePlaySummaryItem> summarizeLanProgramDevices(
            UUID userId,
            String lanProgramId,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort);

    List<MediaPlaySummaryItem> summarizeMedia(UUID userId, Instant from, Instant to, Integer limit, PlaybackSort sort);

    List<PlaybackBucket> getMediaBuckets(
            UUID userId,
            String mediaId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    List<DevicePlaySummaryItem> summarizeMediaDevices(
            UUID userId,
            String mediaId,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort);

    /**
     * 播放记录明细（节目，LAN）- 用于导出。
     */
    List<ProgramPlaySessionItem> listProgramPlaySessionsForLan(
            UUID userId,
            String lanProgramId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorStartAt,
            Long cursorId,
            int limit);

    /**
     * 播放记录明细（节目，平台节目）- 用于导出。
     */
    List<ProgramPlaySessionItem> listProgramPlaySessionsForPlatform(
            UUID userId,
            UUID programId,
            int releaseVersion,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorStartAt,
            Long cursorId,
            int limit);

    /**
     * 播放记录明细（素材）- 用于导出。
     */
    List<MediaPlaySessionItem> listMediaPlaySessions(
            UUID userId,
            String mediaId,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorStartAt,
            Long cursorId,
            int limit);
}
