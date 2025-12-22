package nan.produced.prism.core.program.api.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceSchedulesResp;
import nan.produced.prism.core.program.application.service.ScheduleDeviceDistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部接口-设备排程分发", description = "供 device-service 调用的内部只读接口")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/internal/devices")
public class ScheduleDeviceDistributionInternalController {

    private final ScheduleDeviceDistributionService scheduleDeviceDistributionService;

    @Operation(summary = "查询设备排程信息", description = "返回 Colorlight schedules JSON（供 /wp-json/wp/v3/schedules 适配）。")
    @GetMapping("/{deviceId}/schedules")
    public ResponseEntity<ApiResponse<String>> getSchedules(@PathVariable("deviceId") @NotNull Long deviceId) {
        InternalDeviceSchedulesResp schedules = scheduleDeviceDistributionService.getDeviceSchedules(deviceId);
        String json = JsonUtils.toJson(schedules);
        return ResponseEntity.ok(ApiResponse.success(json).withMeta(TraceUtils.getTraceId(), null, "core-service"));
    }
}
