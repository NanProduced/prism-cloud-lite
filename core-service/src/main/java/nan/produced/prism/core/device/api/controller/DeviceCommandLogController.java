package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogDetailResp;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogPageResp;
import nan.produced.prism.core.device.application.service.DeviceCommandLogApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备指令日志", description = "Dashboard Logs - 设备指令审计日志查询（按当前用户隔离）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device-command-logs")
public class DeviceCommandLogController {

    private final DeviceCommandLogApplicationService deviceCommandLogApplicationService;

    @Operation(
            summary = "查询设备指令日志（分页）",
            description = "支持时间范围、deviceId/deviceName、actionTypes、statuses、accepted/covered、sendMethod 等过滤；按创建时间倒序。operationId/queuedId 为内部调试参数（默认不在 OpenAPI 展示）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回日志分页",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceCommandLogPageResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<DeviceCommandLogPageResp>> listCommandLogs(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "deviceName", required = false) String deviceName,
            @Parameter(hidden = true)
            @RequestParam(value = "operationId", required = false) String operationId,
            @RequestParam(value = "actionTypes", required = false) List<String> actionTypes,
            @RequestParam(value = "statuses", required = false) List<String> statuses,
            @RequestParam(value = "accepted", required = false) Boolean accepted,
            @RequestParam(value = "covered", required = false) Boolean covered,
            @Parameter(hidden = true)
            @RequestParam(value = "queuedId", required = false) Integer queuedId,
            @RequestParam(value = "sendMethod", required = false) String sendMethod,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceCommandLogPageResp resp = deviceCommandLogApplicationService.listCommandLogs(
                userId,
                from,
                to,
                deviceId,
                deviceName,
                operationId,
                actionTypes,
                statuses,
                accepted,
                covered,
                queuedId,
                sendMethod,
                page,
                size
        );
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取设备指令日志详情")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回日志详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceCommandLogDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "日志不存在或无权访问")
    @GetMapping("/{logId}")
    public ResponseEntity<BffResponse<DeviceCommandLogDetailResp>> getCommandLog(@PathVariable("logId") Long logId) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceCommandLogDetailResp detail = deviceCommandLogApplicationService.getCommandLog(userId, logId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }
}
