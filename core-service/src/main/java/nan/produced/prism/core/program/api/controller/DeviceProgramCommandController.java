package nan.produced.prism.core.program.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.dto.DeleteDeviceProgramReq;
import nan.produced.prism.core.device.api.dto.DeleteDeviceProgramResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.program.application.service.DeviceProgramCommandApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备节目管理", description = "设备侧节目清理（删除单个 VSN / 清空全部）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceProgramCommandController {

    private final DeviceProgramCommandApplicationService deviceProgramCommandApplicationService;

    @Operation(summary = "清空设备全部节目", description = "下发清空设备已下载节目指令（DELETE api/clrprgms）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功下发",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceActionDispatchResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @PostMapping("/{deviceId:\\d+}/programs/clear")
    public ResponseEntity<BffResponse<DeviceActionDispatchResp>> clearAll(
        @PathVariable("deviceId") @NotNull Long deviceId) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceActionDispatchResp resp = deviceProgramCommandApplicationService.clearAllPrograms(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除设备指定节目", description = "按 programId 或 vsnName 删除设备上的指定节目（DELETE api/vsns/sources/{source}/vsns/{vsnName}）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功下发（可能会尝试多个 source）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteDeviceProgramResp.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备/节目版本不存在或无权访问")
    @PostMapping("/{deviceId:\\d+}/programs/delete")
    public ResponseEntity<BffResponse<DeleteDeviceProgramResp>> deleteOne(
        @PathVariable("deviceId") @NotNull Long deviceId,
        @RequestBody @Validated DeleteDeviceProgramReq req) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeleteDeviceProgramResp resp = deviceProgramCommandApplicationService.deleteProgram(
            userId,
            deviceId,
            req != null ? req.getProgramId() : null,
            req != null ? req.getVsnName() : null,
            req != null ? req.getSource() : null
        );
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }
}
