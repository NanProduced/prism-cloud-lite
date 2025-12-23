package nan.produced.prism.core.telemetry.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.CloudAuthContext;
import nan.produced.prism.core.telemetry.api.PlaybackTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;
import nan.produced.prism.core.telemetry.api.dto.playback.DevicePlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySummaryItem;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackBucket;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackOverviewResponse;
import nan.produced.prism.core.telemetry.api.dto.playback.PlaybackSort;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySummaryItem;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Telemetry - Playback",
        description = "设备播放统计接口（节目/素材，面向 SPA，经由 Gateway 访问）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/telemetry/playback")
public class PlaybackTelemetryController {

    private final PlaybackTelemetryFacade playbackTelemetryFacade;

    @Operation(summary = "播放统计总览（总计 + Top）")
    @GetMapping("/overview")
    public ResponseEntity<BffResponse<PlaybackOverviewResponse>> overview(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "top", required = false) Integer top,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        PlaybackOverviewResponse data = playbackTelemetryFacade.getOverview(userId, fromInstant, toInstant, top, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "节目播放汇总（按节目版本聚合）")
    @GetMapping("/programs/summary")
    public ResponseEntity<BffResponse<List<ProgramPlaySummaryItem>>> summarizePrograms(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        List<ProgramPlaySummaryItem> data = playbackTelemetryFacade.summarizePrograms(userId, fromInstant, toInstant, limit, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "节目播放趋势（分桶）")
    @GetMapping("/programs/{programId}/versions/{version}/buckets")
    public ResponseEntity<BffResponse<List<PlaybackBucket>>> getProgramBuckets(
            @PathVariable("programId") UUID programId,
            @PathVariable("version") Integer version,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam("tz") String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<PlaybackBucket> data = playbackTelemetryFacade.getProgramBuckets(userId, programId, version, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "节目播放设备分布（按设备聚合）")
    @GetMapping("/programs/{programId}/versions/{version}/devices")
    public ResponseEntity<BffResponse<List<DevicePlaySummaryItem>>> summarizeProgramDevices(
            @PathVariable("programId") UUID programId,
            @PathVariable("version") Integer version,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        List<DevicePlaySummaryItem> data = playbackTelemetryFacade.summarizeProgramDevices(userId, programId, version, fromInstant, toInstant, limit, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "LAN 节目播放趋势（分桶）")
    @GetMapping("/programs/lan/{lanProgramId}/buckets")
    public ResponseEntity<BffResponse<List<PlaybackBucket>>> getLanProgramBuckets(
            @PathVariable("lanProgramId") String lanProgramId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam("tz") String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<PlaybackBucket> data = playbackTelemetryFacade.getLanProgramBuckets(userId, lanProgramId, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "LAN 节目播放设备分布（按设备聚合）")
    @GetMapping("/programs/lan/{lanProgramId}/devices")
    public ResponseEntity<BffResponse<List<DevicePlaySummaryItem>>> summarizeLanProgramDevices(
            @PathVariable("lanProgramId") String lanProgramId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        List<DevicePlaySummaryItem> data = playbackTelemetryFacade.summarizeLanProgramDevices(userId, lanProgramId, fromInstant, toInstant, limit, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "素材播放汇总（按素材聚合）")
    @GetMapping("/media/summary")
    public ResponseEntity<BffResponse<List<MediaPlaySummaryItem>>> summarizeMedia(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        List<MediaPlaySummaryItem> data = playbackTelemetryFacade.summarizeMedia(userId, fromInstant, toInstant, limit, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "素材播放趋势（分桶）")
    @GetMapping("/media/{mediaId}/buckets")
    public ResponseEntity<BffResponse<List<PlaybackBucket>>> getMediaBuckets(
            @PathVariable("mediaId") String mediaId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam("tz") String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<PlaybackBucket> data = playbackTelemetryFacade.getMediaBuckets(userId, mediaId, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "素材播放设备分布（按设备聚合）")
    @GetMapping("/media/{mediaId}/devices")
    public ResponseEntity<BffResponse<List<DevicePlaySummaryItem>>> summarizeMediaDevices(
            @PathVariable("mediaId") String mediaId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sort", required = false) String sort) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        PlaybackSort sortEnum = parseSort(sort);

        List<DevicePlaySummaryItem> data = playbackTelemetryFacade.summarizeMediaDevices(userId, mediaId, fromInstant, toInstant, limit, sortEnum);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    private UUID currentUserId() {
        return UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
    }

    private Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, fieldName + " 不能为空");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST,
                    fieldName + " 必须为 ISO 8601 时间，例如 2025-12-13T02:37:19.000Z");
        }
    }

    private TimeBucketUnit parseBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucket 不能为空");
        }
        try {
            return TimeBucketUnit.valueOf(bucket.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucket 不合法，支持: HOUR/DAY/WEEK/MONTH");
        }
    }

    private PlaybackSort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return PlaybackSort.PLAY_SECONDS;
        }
        String s = sort.trim();
        if ("playSeconds".equalsIgnoreCase(s)) {
            return PlaybackSort.PLAY_SECONDS;
        }
        if ("playCount".equalsIgnoreCase(s)) {
            return PlaybackSort.PLAY_COUNT;
        }
        try {
            return PlaybackSort.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "sort 不合法，支持: playSeconds/playCount");
        }
    }

    private void validateTz(String tzId) {
        try {
            ZoneId.of(tzId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "tz 不合法: " + tzId);
        }
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

