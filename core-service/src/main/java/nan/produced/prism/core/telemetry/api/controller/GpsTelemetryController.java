package nan.produced.prism.core.telemetry.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.telemetry.api.GpsTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsDeviceLocationItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsHeatmapCellItem;
import nan.produced.prism.core.telemetry.api.dto.gps.GpsPointItem;
import nan.produced.prism.core.telemetry.api.dto.gps.ManualLocationItem;
import nan.produced.prism.core.telemetry.api.dto.gps.ManualLocationRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Telemetry - GPS",
    description = "Map：GPS 定位数据与手动坐标接口（面向 SPA，经由 Gateway 访问）。")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telemetry/gps")
public class GpsTelemetryController {

    private final GpsTelemetryFacade gpsTelemetryFacade;

    @Operation(summary = "查询设备最新坐标（上报 + 手动）")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回设备位置列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = GpsDeviceLocationItem.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/latest")
    public ResponseEntity<BffResponse<List<GpsDeviceLocationItem>>> listLatestLocations() {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<GpsDeviceLocationItem> list = gpsTelemetryFacade.listLatestLocations(userId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询设备轨迹点")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回轨迹点",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = GpsPointItem.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/track")
    public ResponseEntity<BffResponse<List<GpsPointItem>>> listTrack(
        @RequestParam("deviceId") Long deviceId,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<GpsPointItem> list = gpsTelemetryFacade.listTrack(
            userId,
            deviceId,
            from != null ? from.toInstant() : null,
            to != null ? to.toInstant() : null,
            limit
        );
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询 GPS 热力图聚合")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回热力图桶",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = GpsHeatmapCellItem.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/heatmap")
    public ResponseEntity<BffResponse<List<GpsHeatmapCellItem>>> heatmap(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "precision", defaultValue = "2") int precision,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<GpsHeatmapCellItem> list = gpsTelemetryFacade.heatmap(
            userId,
            from != null ? from.toInstant() : null,
            to != null ? to.toInstant() : null,
            precision,
            limit
        );
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "设置/更新设备手动坐标")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回最新手动坐标",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ManualLocationItem.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @PostMapping("/overrides/{deviceId}")
    public ResponseEntity<BffResponse<ManualLocationItem>> upsertManualLocation(
        @PathVariable("deviceId") Long deviceId,
        @RequestBody @Valid ManualLocationRequest request
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        ManualLocationItem item = gpsTelemetryFacade.upsertManualLocation(
            userId,
            deviceId,
            request.longitude(),
            request.latitude()
        );
        return ResponseEntity.ok(BffResponse.success(item).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除设备手动坐标")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/overrides/{deviceId}/delete")
    public ResponseEntity<BffResponse<Object>> deleteManualLocation(@PathVariable("deviceId") Long deviceId) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        gpsTelemetryFacade.deleteManualLocation(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }
}
