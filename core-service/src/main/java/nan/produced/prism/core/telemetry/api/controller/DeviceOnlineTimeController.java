package nan.produced.prism.core.telemetry.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.telemetry.api.DeviceOnlineTimeFacade;
import nan.produced.prism.core.telemetry.api.dto.ActiveDeviceCountBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceConcurrencyBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOfflineGapStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionItem;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeDeviceSummary;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Tag(
        name = "Telemetry - Online Time",
        description = """
                设备在线时长统计接口（面向 SPA，经由 Gateway 访问）。

                时间参数约定：
                - `from`/`to`/`cursor`：ISO 8601 时间戳（必须带时区/偏移），建议统一使用 UTC（以 `Z` 结尾），例如 `2025-12-13T02:37:19.000Z`。
                - `tz`：用于“分桶边界对齐”的时区（通常取用户/客户端时区），仅影响按天/周/月等分桶的起止边界，不改变 `from`/`to` 的绝对时间含义。
                """)
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/telemetry/online-time")
public class DeviceOnlineTimeController {

    private final DeviceOnlineTimeFacade deviceOnlineTimeFacade;

    @Operation(
            summary = "查询用户各设备在线总时长（范围聚合）",
            description = """
                    返回当前用户在时间窗内每台设备的在线总秒数（按在线区间与时间窗的交集计算）。

                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    - 本接口不使用 `tz`，因为仅做时间窗聚合，不涉及“按天/周”分桶边界对齐。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回汇总",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceOnlineTimeDeviceSummary.class))))
    @GetMapping("/devices/summary")
    public ResponseEntity<BffResponse<List<DeviceOnlineTimeDeviceSummary>>> summarizeDevices(
            @RequestParam("from") String from,
            @RequestParam("to") String to) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");

        List<DeviceOnlineTimeDeviceSummary> data = deviceOnlineTimeFacade.summarizeDevices(userId, fromInstant, toInstant);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询单设备在线时长分桶聚合",
            description = """
                    按时间桶统计单设备在线时长（秒）与在线率（0~1）。

                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    - `tz`：用于“分桶边界对齐”的时区（通常取用户/客户端时区），仅影响桶的起止边界：
                      - 例如“每天 00:00”在 `Asia/Shanghai` 与 `Asia/Kathmandu` 对应的 UTC 起点不同；
                      - 支持 IANA 时区：`Asia/Shanghai`、`Asia/Kathmandu`；
                      - 也支持 offset：`UTC+8`、`+08:00`、`UTC+05:45`。
                    - 返回的 `bucketStart`/`bucketEnd` 为 UTC 时间戳（`Z`），前端按需用 `tz` 转换展示。
                    - `bucket`：`HOUR`/`DAY`/`WEEK`/`MONTH`；其中 `WEEK` 按 ISO 周（周一 00:00）对齐。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回分桶聚合",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceOnlineTimeBucket.class))))
    @GetMapping("/devices/{deviceId:\\d+}/buckets")
    public ResponseEntity<BffResponse<List<DeviceOnlineTimeBucket>>> getDeviceBuckets(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "tz", required = false) String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<DeviceOnlineTimeBucket> data = deviceOnlineTimeFacade.getDeviceBuckets(userId, deviceId, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询活跃设备数分桶聚合",
            description = """
                    查询用户在时间窗内的活跃设备数时间序列（按桶聚合）。

                    - 活跃判定：设备在桶内在线秒数 > 0 即视为活跃（计入桶内活跃设备数）。
                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    - `tz`：用于“分桶边界对齐”的时区（通常取用户/客户端时区），仅影响桶起止边界；返回桶起止为 UTC（`Z`）。
                    - `bucket`：`HOUR`/`DAY`/`WEEK`/`MONTH`；其中 `WEEK` 按 ISO 周（周一 00:00）对齐。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回活跃设备数序列",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ActiveDeviceCountBucket.class))))
    @GetMapping("/devices/active-count/buckets")
    public ResponseEntity<BffResponse<List<ActiveDeviceCountBucket>>> getActiveDeviceCountBuckets(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "tz", required = false) String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<ActiveDeviceCountBucket> data = deviceOnlineTimeFacade.getActiveDeviceCountBuckets(userId, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询并发在线分桶聚合",
            description = """
                    查询用户在时间窗内的并发在线统计（按桶聚合）。

                    - `avgConcurrent`：桶内 time-weighted mean（= 桶内“设备在线秒数”总和 / 桶有效秒数）。
                    - `maxConcurrent`：桶内并发峰值（基于在线区间的起止事件计算）。
                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    - `tz`：用于“分桶边界对齐”的时区（通常取用户/客户端时区），仅影响桶起止边界；返回桶起止为 UTC（`Z`）。
                    - `bucket`：`HOUR`/`DAY`/`WEEK`/`MONTH`；其中 `WEEK` 按 ISO 周（周一 00:00）对齐。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回并发在线序列",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceConcurrencyBucket.class))))
    @GetMapping("/devices/concurrency/buckets")
    public ResponseEntity<BffResponse<List<DeviceConcurrencyBucket>>> getConcurrencyBuckets(
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "tz", required = false) String tz,
            @RequestParam("bucket") String bucket) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        String tzId = normalizeTz(tz);
        validateTz(tzId);
        TimeBucketUnit unit = parseBucket(bucket);

        List<DeviceConcurrencyBucket> data = deviceOnlineTimeFacade.getConcurrencyBuckets(userId, fromInstant, toInstant, tzId, unit);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询单设备在线会话统计",
            description = """
                    返回单设备在时间窗内的会话统计（按在线区间与时间窗的交集计算）。

                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    - 会话时长按“有效区间”计算：`max(onlineAt, from)` ~ `min(offlineAt, to)`。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回会话统计",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceOnlineSessionStats.class)))
    @GetMapping("/devices/{deviceId:\\d+}/session-stats")
    public ResponseEntity<BffResponse<DeviceOnlineSessionStats>> getDeviceSessionStats(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("from") String from,
            @RequestParam("to") String to) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");

        DeviceOnlineSessionStats data = deviceOnlineTimeFacade.getDeviceSessionStats(userId, deviceId, fromInstant, toInstant);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询单设备离线间隔统计",
            description = """
                    查询单设备在时间窗内的离线间隔（gap）统计，用于评估连接稳定性。

                    - gap 定义：相邻两段在线区间之间的间隔（下一段 onlineAt - 上一段 offlineAt）。
                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义统计窗口（绝对时间）。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回离线间隔统计",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceOfflineGapStats.class)))
    @GetMapping("/devices/{deviceId:\\d+}/offline-gap-stats")
    public ResponseEntity<BffResponse<DeviceOfflineGapStats>> getDeviceOfflineGapStats(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("from") String from,
            @RequestParam("to") String to) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");

        DeviceOfflineGapStats data = deviceOnlineTimeFacade.getDeviceOfflineGapStats(userId, deviceId, fromInstant, toInstant);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询单设备在线会话明细",
            description = """
                    返回上线-下线区间明细（按 onlineAt 倒序），用于报表下钻/对账。

                    - `from`/`to`：ISO 8601 时间戳（建议 UTC `Z` 结尾），定义查询窗口（绝对时间）。
                    - 返回 `onlineAt`/`offlineAt` 与 `effectiveOnlineAt`/`effectiveOfflineAt` 均为 UTC（`Z`）。
                    - `cursor`：用于倒序分页的游标（ISO 8601 时间戳，建议 UTC `Z` 结尾），语义为：仅返回 `onlineAt < cursor` 的记录。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回会话列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceOnlineSessionItem.class))))
    @GetMapping("/devices/{deviceId:\\d+}/sessions")
    public ResponseEntity<BffResponse<List<DeviceOnlineSessionItem>>> listDeviceSessions(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor) {

        UUID userId = currentUserId();
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        Instant cursorInstant = cursor == null || cursor.isBlank() ? null : parseInstant(cursor, "cursor");

        List<DeviceOnlineSessionItem> data = deviceOnlineTimeFacade.listDeviceSessions(userId, deviceId, fromInstant, toInstant, limit, cursorInstant);
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

        try {
            return ZoneOffset.ofHoursMinutes(sign == '-' ? -hour : hour, sign == '-' ? -minute : minute).getId();
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
