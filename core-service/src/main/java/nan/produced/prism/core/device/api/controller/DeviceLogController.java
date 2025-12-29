package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.dto.log.DeviceLogDetailResp;
import nan.produced.prism.core.device.api.dto.log.DeviceLogPageResp;
import nan.produced.prism.core.device.api.dto.log.DeviceLogTypeResp;
import nan.produced.prism.core.device.application.service.DeviceLogApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备日志", description = "Dashboard Logs - 设备上报日志查询（按当前用户隔离）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device-logs")
public class DeviceLogController {

    private final DeviceLogApplicationService deviceLogApplicationService;

    @Operation(summary = "查询设备日志（分页）", description = "支持时间范围、deviceId/deviceName、operationIds 过滤；按接收时间倒序。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回日志分页",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceLogPageResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<DeviceLogPageResp>> listDeviceLogs(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "deviceId", required = false) Long deviceId,
        @RequestParam(value = "deviceName", required = false) String deviceName,
        @RequestParam(value = "operationIds", required = false) List<Integer> operationIds,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceLogPageResp resp = deviceLogApplicationService.listDeviceLogs(userId, from, to, deviceId, deviceName, operationIds, page, size);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取设备日志详情")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回日志详情",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceLogDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "日志不存在或无权访问")
    @GetMapping("/{logId}")
    public ResponseEntity<BffResponse<DeviceLogDetailResp>> getDeviceLog(@PathVariable("logId") Long logId) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceLogDetailResp detail = deviceLogApplicationService.getDeviceLog(userId, logId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取日志 operation_id 字典", description = "返回 DeviceLogType 全量枚举，供前端筛选与展示。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回操作类型列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceLogTypeResp.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/types")
    public ResponseEntity<BffResponse<List<DeviceLogTypeResp>>> listTypes() {
        List<DeviceLogTypeResp> types = deviceLogApplicationService.listDeviceLogTypes();
        return ResponseEntity.ok(BffResponse.success(types).withTraceId(TraceUtils.getTraceId()));
    }
}
