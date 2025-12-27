package nan.produced.prism.core.device.api.controller;

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
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchReq;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.device.application.port.inbound.DeviceActionDispatchUseCase;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备指令（动作）", description = "面向 SPA 的设备指令下发入口（单条/批量）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceActionController {

    private final DeviceActionDispatchUseCase deviceActionDispatchUseCase;

    @Operation(summary = "单设备下发指令（动作）", description = "下发一条设备指令；返回 operationId（建议等同 commandId）用于后续 SSE 追踪。")
    @ApiResponse(
            responseCode = "200",
            description = "成功下发",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceActionDispatchResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @PostMapping({"/{deviceId:\\d+}/actions", "/{deviceId:\\d+}/commands"})
    public ResponseEntity<BffResponse<DeviceActionDispatchResp>> dispatchSingle(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @RequestBody @Validated DeviceActionBase action) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        DeviceActionDispatchResp resp = deviceActionDispatchUseCase.dispatchSingle(userId, deviceId, action);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "批量下发指令（动作）", description = "批量下发设备指令；每个 item 可携带不同 type/body。")
    @ApiResponse(
            responseCode = "200",
            description = "成功下发（批量）",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchDeviceActionDispatchResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping({"/actions/batch", "/commands/batch"})
    public ResponseEntity<BffResponse<BatchDeviceActionDispatchResp>> dispatchBatch(
            @RequestBody @Validated BatchDeviceActionDispatchReq req) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        BatchDeviceActionDispatchResp resp = deviceActionDispatchUseCase.dispatchBatch(userId, req);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }
}
