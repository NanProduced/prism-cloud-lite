package nan.produced.prism.core.program.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.program.api.dto.schedule.DeviceProgramAllowlistResp;
import nan.produced.prism.core.program.api.dto.schedule.DeviceScheduleResp;
import nan.produced.prism.core.program.application.service.ScheduleApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备排程", description = "用于设备详情页展示排程/允许集/调试信息（面向 SPA，经由 Gateway 访问）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceScheduleController {

    private final ScheduleApplicationService scheduleApplicationService;

    @Operation(summary = "查询设备当前绑定排程（含规则）", description = "返回设备绑定的排程摘要、规则列表与统计信息。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回设备排程信息",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceScheduleResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @GetMapping("/{deviceId:\\d+}/schedule")
    public ResponseEntity<BffResponse<DeviceScheduleResp>> getDeviceSchedule(@PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        DeviceScheduleResp resp = scheduleApplicationService.getDeviceSchedule(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询设备排程 JSON（Colorlight）", description = "返回设备侧拉取 /wp-json/wp/v3/schedules 的 schedules JSON（用于 Debug）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回 schedules JSON",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @GetMapping("/{deviceId:\\d+}/schedule-json")
    public ResponseEntity<BffResponse<JsonNode>> getDeviceScheduleJson(@PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        JsonNode json = scheduleApplicationService.getDeviceScheduleJson(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(json).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询设备节目允许集（AllowList）", description = "返回 Assignment ∪ ScheduleContents 的并集（带来源与下载状态，用于 Device Visibility）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回允许集列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceProgramAllowlistResp.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @GetMapping("/{deviceId:\\d+}/program-allowlist")
    public ResponseEntity<BffResponse<List<DeviceProgramAllowlistResp>>> listDeviceProgramAllowlist(
            @PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<DeviceProgramAllowlistResp> list = scheduleApplicationService.listDeviceProgramAllowlist(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }
}
