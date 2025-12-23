package nan.produced.prism.core.telemetry.application.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.domain.report.media.MediaPlayTimesReport;
import nan.produced.prism.core.device.domain.report.program.ProgramPlayTimesReport;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.infrastructure.persistence.MediaAssetRepositoryJpa;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import nan.produced.prism.core.telemetry.api.PlaybackTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;
import nan.produced.prism.core.telemetry.api.dto.playback.DevicePlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackBucket;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackOverviewResponse;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackSort;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackTotals;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySummaryItem;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceMediaPlaySessionRepository;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceProgramPlaySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackTelemetryApplicationService implements PlaybackTelemetryFacade {

    private static final int DEFAULT_TOP = 10;
    private static final int MAX_TOP = 50;

    private static final int DEFAULT_LIST_LIMIT = 200;
    private static final int MAX_LIST_LIMIT = 1000;

    private final DeviceProgramPlaySessionRepository deviceProgramPlaySessionRepository;
    private final DeviceMediaPlaySessionRepository deviceMediaPlaySessionRepository;

    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;
    private final MediaAssetRepositoryJpa mediaAssetRepositoryJpa;

    @Override
    @Transactional
    public void recordProgramPlayRecords(UUID userId, Long deviceId, List<ProgramPlayTimesReport> reports, String traceId) {
        if (userId == null || deviceId == null || reports == null || reports.isEmpty()) {
            return;
        }

        Map<String, ProgramReleaseEntity> releaseCache = new HashMap<>();
        Set<String> releaseMiss = new HashSet<>();
        List<DeviceProgramPlaySessionRepository.InsertRow> rows = new ArrayList<>();

        for (ProgramPlayTimesReport report : reports) {
            if (report == null) {
                continue;
            }

            boolean isLan = StringUtils.hasText(report.getIdStr());
            String lanProgramId = isLan ? report.getIdStr().trim() : null;
            String programVsn = StringUtils.hasText(report.getProgramVsn()) ? report.getProgramVsn().trim() : null;
            String programNameSnapshot = StringUtils.hasText(report.getProgramName()) ? report.getProgramName().trim() : null;

            UUID programId = null;
            Integer releaseVersion = null;
            VsnMeta meta = parseVsnMeta(programVsn);
            String vsnMd5 = meta != null ? meta.md5() : null;
            Long vsnSizeBytes = meta != null ? meta.sizeBytes() : null;

            if (!isLan) {
                if (meta == null) {
                    log.warn("PlaybackTelemetry - ProgramPlayRecord VSN 解析失败，跳过: deviceId={}, programVsn={}, traceId={}",
                            deviceId, programVsn, traceId);
                    continue;
                }
                ProgramReleaseEntity release = resolveRelease(userId, meta, releaseCache, releaseMiss);
                if (release == null) {
                    log.warn("PlaybackTelemetry - ProgramPlayRecord 未找到对应 Release，跳过: deviceId={}, vsnMd5={}, vsnSizeBytes={}, traceId={}",
                            deviceId, vsnMd5, vsnSizeBytes, traceId);
                    continue;
                }
                programId = release.getProgramId();
                releaseVersion = release.getVersion();
            }

            List<OffsetDateTime> starts = report.getStartUtcTime() != null ? report.getStartUtcTime() : List.of();
            List<OffsetDateTime> ends = report.getEndUtcTime() != null ? report.getEndUtcTime() : List.of();
            int n = Math.min(starts.size(), ends.size());
            if (n <= 0) {
                continue;
            }

            for (int i = 0; i < n; i++) {
                OffsetDateTime startAt = normalizeReportedUtcTime(starts.get(i));
                OffsetDateTime endAt = normalizeReportedUtcTime(ends.get(i));
                if (!isValidRange(startAt, endAt)) {
                    continue;
                }

                rows.add(new DeviceProgramPlaySessionRepository.InsertRow(
                        userId,
                        deviceId,
                        isLan,
                        lanProgramId,
                        programId,
                        releaseVersion,
                        programVsn,
                        programNameSnapshot,
                        vsnMd5,
                        vsnSizeBytes,
                        startAt,
                        endAt
                ));
            }
        }

        int inserted = deviceProgramPlaySessionRepository.insertIgnoreBatch(rows);
        if (inserted > 0) {
            log.debug("PlaybackTelemetry - ProgramPlayRecord 落库完成: deviceId={}, inserted={}, traceId={}",
                    deviceId, inserted, traceId);
        }
    }

    @Override
    @Transactional
    public void recordMediaPlayRecords(UUID userId, Long deviceId, List<MediaPlayTimesReport> reports, String traceId) {
        if (userId == null || deviceId == null || reports == null || reports.isEmpty()) {
            return;
        }

        Map<String, ProgramReleaseEntity> releaseCache = new HashMap<>();
        Set<String> releaseMiss = new HashSet<>();
        List<DeviceMediaPlaySessionRepository.InsertRow> rows = new ArrayList<>();

        for (MediaPlayTimesReport report : reports) {
            if (report == null) {
                continue;
            }

            String mediaId = StringUtils.hasText(report.getResOriginName()) ? report.getResOriginName().trim() : null;
            if (!StringUtils.hasText(mediaId)) {
                continue;
            }

            OffsetDateTime startAt = normalizeReportedUtcTime(report.getStartUtcTime());
            OffsetDateTime endAt = normalizeReportedUtcTime(report.getEndUtcTime());
            if (!isValidRange(startAt, endAt)) {
                continue;
            }

            String programVsn = StringUtils.hasText(report.getProgramName()) ? report.getProgramName().trim() : null;
            VsnMeta meta = parseVsnMeta(programVsn);

            boolean isLan = true;
            UUID programId = null;
            Integer releaseVersion = null;
            String vsnMd5 = meta != null ? meta.md5() : null;
            Long vsnSizeBytes = meta != null ? meta.sizeBytes() : null;

            if (meta != null) {
                ProgramReleaseEntity release = resolveRelease(userId, meta, releaseCache, releaseMiss);
                if (release != null) {
                    isLan = false;
                    programId = release.getProgramId();
                    releaseVersion = release.getVersion();
                }
            }

            rows.add(new DeviceMediaPlaySessionRepository.InsertRow(
                    userId,
                    deviceId,
                    mediaId,
                    report.getResOriginName(),
                    report.getResMd5Name(),
                    report.getItemType(),
                    isLan,
                    programId,
                    releaseVersion,
                    programVsn,
                    null,
                    vsnMd5,
                    vsnSizeBytes,
                    report.getPageName(),
                    report.getPageIndex(),
                    report.getRegionName(),
                    report.getRegionIndex(),
                    startAt,
                    endAt,
                    report.getDuration()
            ));
        }

        int inserted = deviceMediaPlaySessionRepository.insertIgnoreBatch(rows);
        if (inserted > 0) {
            log.debug("PlaybackTelemetry - MediaPlayRecord 落库完成: deviceId={}, inserted={}, traceId={}",
                    deviceId, inserted, traceId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PlaybackOverviewResponse getOverview(UUID userId, Instant from, Instant to, Integer top, PlaybackSort sort) {
        validateRange(userId, from, to);

        int safeTop = normalizeLimit(top, DEFAULT_TOP, MAX_TOP);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        DeviceProgramPlaySessionRepository.TotalsRow programTotals = deviceProgramPlaySessionRepository.totals(userId, fromUtc, toUtc);
        DeviceMediaPlaySessionRepository.TotalsRow mediaTotals = deviceMediaPlaySessionRepository.totals(userId, fromUtc, toUtc);

        List<ProgramPlaySummaryItem> topPrograms = mapPrograms(
                userId,
                deviceProgramPlaySessionRepository.summarize(userId, fromUtc, toUtc, safeTop, orderBySeconds));

        List<MediaPlaySummaryItem> topMedia = mapMedia(
                userId,
                deviceMediaPlaySessionRepository.summarize(userId, fromUtc, toUtc, safeTop, orderBySeconds));

        return new PlaybackOverviewResponse(
                new PlaybackTotals(programTotals.playCount(), programTotals.playSeconds(), programTotals.deviceCount()),
                new PlaybackTotals(mediaTotals.playCount(), mediaTotals.playSeconds(), mediaTotals.deviceCount()),
                topPrograms,
                topMedia
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramPlaySummaryItem> summarizePrograms(UUID userId, Instant from, Instant to, Integer limit, PlaybackSort sort) {
        validateRange(userId, from, to);
        int safeLimit = normalizeLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceProgramPlaySessionRepository.ProgramSummaryRow> rows =
                deviceProgramPlaySessionRepository.summarize(userId, fromUtc, toUtc, safeLimit, orderBySeconds);

        return mapPrograms(userId, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaybackBucket> getProgramBuckets(
            UUID userId,
            UUID programId,
            Integer releaseVersion,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (programId == null || releaseVersion == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "programId/releaseVersion 不能为空");
        }
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceProgramPlaySessionRepository.BucketRow> rows =
                deviceProgramPlaySessionRepository.bucketsForPlatform(
                        userId,
                        programId,
                        releaseVersion,
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .map(r -> new PlaybackBucket(
                        r.bucketStart().toInstant(),
                        r.bucketEnd().toInstant(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        Math.max(0L, r.deviceCount())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevicePlaySummaryItem> summarizeProgramDevices(
            UUID userId,
            UUID programId,
            Integer releaseVersion,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort) {

        validateRange(userId, from, to);
        if (programId == null || releaseVersion == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "programId/releaseVersion 不能为空");
        }

        int safeLimit = normalizeLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceProgramPlaySessionRepository.DeviceSummaryRow> rows =
                deviceProgramPlaySessionRepository.summarizeDevicesForPlatform(
                        userId,
                        programId,
                        releaseVersion,
                        fromUtc,
                        toUtc,
                        safeLimit,
                        orderBySeconds
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(r -> new DevicePlaySummaryItem(
                        r.deviceId(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        r.lastPlayedAt() != null ? r.lastPlayedAt().toInstant() : null
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaybackBucket> getLanProgramBuckets(
            UUID userId,
            String lanProgramId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (!StringUtils.hasText(lanProgramId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "lanProgramId 不能为空");
        }
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceProgramPlaySessionRepository.BucketRow> rows =
                deviceProgramPlaySessionRepository.bucketsForLan(
                        userId,
                        lanProgramId.trim(),
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .map(r -> new PlaybackBucket(
                        r.bucketStart().toInstant(),
                        r.bucketEnd().toInstant(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        Math.max(0L, r.deviceCount())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevicePlaySummaryItem> summarizeLanProgramDevices(
            UUID userId,
            String lanProgramId,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort) {

        validateRange(userId, from, to);
        if (!StringUtils.hasText(lanProgramId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "lanProgramId 不能为空");
        }

        int safeLimit = normalizeLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceProgramPlaySessionRepository.DeviceSummaryRow> rows =
                deviceProgramPlaySessionRepository.summarizeDevicesForLan(
                        userId,
                        lanProgramId.trim(),
                        fromUtc,
                        toUtc,
                        safeLimit,
                        orderBySeconds
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(r -> new DevicePlaySummaryItem(
                        r.deviceId(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        r.lastPlayedAt() != null ? r.lastPlayedAt().toInstant() : null
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaPlaySummaryItem> summarizeMedia(UUID userId, Instant from, Instant to, Integer limit, PlaybackSort sort) {
        validateRange(userId, from, to);
        int safeLimit = normalizeLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceMediaPlaySessionRepository.MediaSummaryRow> rows =
                deviceMediaPlaySessionRepository.summarize(userId, fromUtc, toUtc, safeLimit, orderBySeconds);

        return mapMedia(userId, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaybackBucket> getMediaBuckets(
            UUID userId,
            String mediaId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (!StringUtils.hasText(mediaId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "mediaId 不能为空");
        }
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceMediaPlaySessionRepository.BucketRow> rows =
                deviceMediaPlaySessionRepository.buckets(
                        userId,
                        mediaId.trim(),
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .map(r -> new PlaybackBucket(
                        r.bucketStart().toInstant(),
                        r.bucketEnd().toInstant(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        Math.max(0L, r.deviceCount())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DevicePlaySummaryItem> summarizeMediaDevices(
            UUID userId,
            String mediaId,
            Instant from,
            Instant to,
            Integer limit,
            PlaybackSort sort) {

        validateRange(userId, from, to);
        if (!StringUtils.hasText(mediaId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "mediaId 不能为空");
        }

        int safeLimit = normalizeLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        boolean orderBySeconds = sort == null || sort == PlaybackSort.PLAY_SECONDS;

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceMediaPlaySessionRepository.DeviceSummaryRow> rows =
                deviceMediaPlaySessionRepository.summarizeDevices(
                        userId,
                        mediaId.trim(),
                        fromUtc,
                        toUtc,
                        safeLimit,
                        orderBySeconds
                );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .map(r -> new DevicePlaySummaryItem(
                        r.deviceId(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        r.lastPlayedAt() != null ? r.lastPlayedAt().toInstant() : null
                ))
                .toList();
    }

    private List<ProgramPlaySummaryItem> mapPrograms(UUID userId, List<DeviceProgramPlaySessionRepository.ProgramSummaryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> programIds = new HashSet<>();
        for (var row : rows) {
            if (!row.lan() && row.programId() != null) {
                programIds.add(row.programId());
            }
        }

        Map<UUID, String> namesById = new HashMap<>();
        if (!programIds.isEmpty()) {
            for (ProgramEntity program : programRepositoryJpa.findAllById(programIds)) {
                if (program == null) {
                    continue;
                }
                if (userId.equals(program.getUserId())) {
                    namesById.put(program.getId(), program.getName());
                }
            }
        }

        return rows.stream()
                .map(r -> {
                    String programName;
                    if (r.lan()) {
                        programName = firstNonBlank(r.programNameSnapshot(), r.programVsn(), r.lanProgramId());
                    } else {
                        programName = firstNonBlank(namesById.get(r.programId()), r.programNameSnapshot(), r.programVsn());
                    }
                    return new ProgramPlaySummaryItem(
                            r.lan(),
                            r.programId(),
                            r.releaseVersion(),
                            r.lanProgramId(),
                            programName,
                            Math.max(0L, r.playCount()),
                            Math.max(0L, r.playSeconds()),
                            Math.max(0L, r.deviceCount()),
                            r.lastPlayedAt() != null ? r.lastPlayedAt().toInstant() : null
                    );
                })
                .toList();
    }

    private List<MediaPlaySummaryItem> mapMedia(UUID userId, List<DeviceMediaPlaySessionRepository.MediaSummaryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Set<String> mediaIds = new HashSet<>();
        for (var row : rows) {
            if (StringUtils.hasText(row.mediaId())) {
                mediaIds.add(row.mediaId());
            }
        }

        Map<String, String> titlesById = new HashMap<>();
        if (!mediaIds.isEmpty()) {
            for (MediaAssetEntity asset : mediaAssetRepositoryJpa.findAllById(mediaIds)) {
                if (asset == null) {
                    continue;
                }
                if (userId.equals(asset.getUserId())) {
                    titlesById.put(asset.getId(), asset.getTitle());
                }
            }
        }

        return rows.stream()
                .map(r -> new MediaPlaySummaryItem(
                        r.mediaId(),
                        titlesById.get(r.mediaId()),
                        r.itemType(),
                        Math.max(0L, r.playCount()),
                        Math.max(0L, r.playSeconds()),
                        Math.max(0L, r.deviceCount()),
                        r.lastPlayedAt() != null ? r.lastPlayedAt().toInstant() : null
                ))
                .toList();
    }

    private void validateRange(UUID userId, Instant from, Instant to) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (from == null || to == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "from/to 不能为空");
        }
        if (!from.isBefore(to)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "from 必须早于 to");
        }
    }

    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private OffsetDateTime normalizeReportedUtcTime(OffsetDateTime reported) {
        if (reported == null) {
            return null;
        }
        return reported.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    private boolean isValidRange(OffsetDateTime startAt, OffsetDateTime endAt) {
        return startAt != null && endAt != null && startAt.isBefore(endAt);
    }

    private record VsnMeta(String md5, long sizeBytes) {
    }

    private VsnMeta parseVsnMeta(String vsn) {
        if (!StringUtils.hasText(vsn)) {
            return null;
        }
        String s = vsn.trim();
        int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash + 1 < s.length()) {
            s = s.substring(lastSlash + 1);
        }
        if (s.endsWith(".vsn") || s.endsWith(".VSN")) {
            s = s.substring(0, s.length() - 4);
        }

        int lastUnderscore = s.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= s.length() - 1) {
            return null;
        }
        int secondLastUnderscore = s.lastIndexOf('_', lastUnderscore - 1);
        if (secondLastUnderscore <= 0 || secondLastUnderscore >= lastUnderscore - 1) {
            return null;
        }

        String md5 = s.substring(secondLastUnderscore + 1, lastUnderscore).trim();
        String sizeStr = s.substring(lastUnderscore + 1).trim();
        if (!md5.matches("(?i)[0-9a-f]{32}")) {
            return null;
        }

        long sizeBytes;
        try {
            sizeBytes = Long.parseLong(sizeStr);
        } catch (NumberFormatException e) {
            return null;
        }

        return new VsnMeta(md5.toUpperCase(), sizeBytes);
    }

    private ProgramReleaseEntity resolveRelease(
            UUID userId,
            VsnMeta meta,
            Map<String, ProgramReleaseEntity> cache,
            Set<String> miss) {

        String key = meta.md5() + ":" + meta.sizeBytes();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (miss.contains(key)) {
            return null;
        }

        ProgramReleaseEntity release = programReleaseRepositoryJpa
                .findByUserIdAndVsnMd5AndVsnSizeBytes(userId, meta.md5(), meta.sizeBytes())
                .orElse(null);

        if (release == null) {
            miss.add(key);
            return null;
        }
        cache.put(key, release);
        return release;
    }

    private String normalizeTz(String tz) {
        if (tz == null || tz.isBlank()) {
            return "UTC";
        }
        String trimmed = tz.trim();
        if ("Z".equalsIgnoreCase(trimmed) || "UTC".equalsIgnoreCase(trimmed)) {
            return "UTC";
        }
        String normalizedOffset = tryNormalizeOffset(trimmed);
        return normalizedOffset != null ? normalizedOffset : trimmed;
    }

    private String tryNormalizeOffset(String tz) {
        String candidate = tz;
        if (candidate.regionMatches(true, 0, "UTC", 0, 3) && candidate.length() > 3) {
            candidate = candidate.substring(3);
        }
        candidate = candidate.trim();

        if (!(candidate.startsWith("+") || candidate.startsWith("-"))) {
            return null;
        }

        char sign = candidate.charAt(0);
        String rest = candidate.substring(1);
        Integer hour;
        Integer minute;

        if (rest.contains(":")) {
            String[] parts = rest.split(":", -1);
            if (parts.length != 2) {
                return null;
            }
            hour = parseInt(parts[0]);
            minute = parseInt(parts[1]);
        } else if (rest.length() == 4) {
            hour = parseInt(rest.substring(0, 2));
            minute = parseInt(rest.substring(2, 4));
        } else {
            hour = parseInt(rest);
            minute = 0;
        }

        if (hour == null || minute == null) {
            return null;
        }
        if (hour < 0 || hour > 18) {
            return null;
        }
        if (minute < 0 || minute >= 60) {
            return null;
        }
        String hh = String.format("%02d", hour);
        String mm = String.format("%02d", minute);
        return String.format("%c%s:%s", sign, hh, mm);
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
