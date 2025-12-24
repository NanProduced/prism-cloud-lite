package nan.produced.prism.core.program.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.program.api.dto.schedule.CreateScheduleReq;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleAuditLogResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindDevicesReq;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindDevicesResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindingDeviceResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleDetailResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleListResp;
import nan.produced.prism.core.program.api.dto.schedule.SchedulePushReq;
import nan.produced.prism.core.program.api.dto.schedule.SchedulePushResp;
import nan.produced.prism.core.program.api.dto.schedule.UpdateScheduleReq;
import nan.produced.prism.core.program.application.service.ScheduleApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "排程", description = "排程管理（Schedules）接口（面向 SPA，经由 Gateway 访问）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleApplicationService scheduleApplicationService;

    @Operation(summary = "查询排程列表", description = "返回当前用户全部排程（Lite 场景默认不分页）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回排程列表",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleListResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<List<ScheduleListResp>>> listSchedules() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ScheduleListResp> list = scheduleApplicationService.listSchedules(userId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "创建排程", description = "创建一个排程，并可选初始化节目/指令规则。")
    @ApiResponse(
            responseCode = "200",
            description = "成功创建排程",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping
    public ResponseEntity<BffResponse<ScheduleDetailResp>> createSchedule(@RequestBody @Valid CreateScheduleReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ScheduleDetailResp created = scheduleApplicationService.createSchedule(userId, req);
        return ResponseEntity.ok(BffResponse.success(created).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取排程详情", description = "返回排程基础信息 + rules + bindings。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回排程详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @GetMapping("/{scheduleId}")
    public ResponseEntity<BffResponse<ScheduleDetailResp>> getSchedule(@PathVariable("scheduleId") @NotNull UUID scheduleId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ScheduleDetailResp detail = scheduleApplicationService.getScheduleDetail(userId, scheduleId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "更新排程", description = "更新排程基础信息；如传入 rules 列表则全量替换。")
    @ApiResponse(
            responseCode = "200",
            description = "成功更新排程",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @PostMapping("/{scheduleId}")
    public ResponseEntity<BffResponse<ScheduleDetailResp>> updateSchedule(
            @PathVariable("scheduleId") @NotNull UUID scheduleId,
            @RequestBody @Valid UpdateScheduleReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ScheduleDetailResp updated = scheduleApplicationService.updateSchedule(userId, scheduleId, req);
        return ResponseEntity.ok(BffResponse.success(updated).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除排程", description = "删除排程及其 rules/bindings（DB 级联）。")
    @ApiResponse(responseCode = "200", description = "成功删除排程")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @PostMapping("/{scheduleId}/delete")
    public ResponseEntity<BffResponse<Void>> deleteSchedule(@PathVariable("scheduleId") @NotNull UUID scheduleId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        scheduleApplicationService.deleteSchedule(userId, scheduleId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询排程绑定设备", description = "返回该排程绑定的设备列表（Lite 默认不分页）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回绑定设备列表",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleBindingDeviceResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @GetMapping("/{scheduleId}/bindings")
    public ResponseEntity<BffResponse<List<ScheduleBindingDeviceResp>>> listBindings(@PathVariable("scheduleId") @NotNull UUID scheduleId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ScheduleBindingDeviceResp> list = scheduleApplicationService.listBindings(userId, scheduleId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "批量绑定设备到排程", description = "一设备最多绑定一套排程；可选 replaceExisting 覆盖绑定。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回绑定结果",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleBindDevicesResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @PostMapping("/{scheduleId}/bindings")
    public ResponseEntity<BffResponse<ScheduleBindDevicesResp>> bindDevices(
            @PathVariable("scheduleId") @NotNull UUID scheduleId,
            @RequestBody @Valid ScheduleBindDevicesReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ScheduleBindDevicesResp resp = scheduleApplicationService.bindDevices(userId, scheduleId, req);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "解绑设备排程", description = "解除设备与该排程的绑定关系。")
    @ApiResponse(responseCode = "200", description = "成功解绑")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @PostMapping("/{scheduleId}/bindings/{deviceId}/delete")
    public ResponseEntity<BffResponse<Void>> unbindDevice(
            @PathVariable("scheduleId") @NotNull UUID scheduleId,
            @PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        scheduleApplicationService.unbindDevice(userId, scheduleId, deviceId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "下发排程指令到设备", description = "向目标设备下发 {\"program\":\"schedule\"} 触发终端拉取 /wp-json/wp/v3/schedules。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回下发结果",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SchedulePushResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @PostMapping("/{scheduleId}/push")
    public ResponseEntity<BffResponse<SchedulePushResp>> pushSchedule(
            @PathVariable("scheduleId") @NotNull UUID scheduleId,
            @RequestBody(required = false) SchedulePushReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        SchedulePushResp resp = scheduleApplicationService.pushSchedule(userId, scheduleId, req);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询排程变更日志", description = "Lite：用于排程详情页展示变更历史（按时间倒序）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回变更日志",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ScheduleAuditLogResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "排程不存在或无权访问")
    @GetMapping("/{scheduleId}/audit-logs")
    public ResponseEntity<BffResponse<List<ScheduleAuditLogResp>>> listAuditLogs(
            @PathVariable("scheduleId") @NotNull UUID scheduleId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ScheduleAuditLogResp> logs = scheduleApplicationService.listAuditLogs(userId, scheduleId);
        return ResponseEntity.ok(BffResponse.success(logs).withTraceId(TraceUtils.getTraceId()));
    }
}
