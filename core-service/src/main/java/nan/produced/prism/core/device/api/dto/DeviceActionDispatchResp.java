package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.device.domain.command.DeviceActionTrackingLevel;
import nan.produced.prism.core.device.domain.command.DeviceActionType;

@Schema(description = "设备动作下发结果（单设备）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceActionDispatchResp {

    @Schema(description = "操作ID（等同 commandId，用于后续 SSE 追踪）", example = "8f3c0d2e-3c5b-4d8c-9f6f-8d1a0b2d7f90")
    private String operationId;

    @Schema(description = "设备ID", example = "10001")
    private Long deviceId;

    @Schema(description = "动作类型", example = "BRIGHTNESS")
    private DeviceActionType type;

    @Schema(description = "追踪等级", example = "PROPERTY_MATCH")
    private DeviceActionTrackingLevel trackingLevel;

    @Schema(description = "下发状态", example = "DISPATCHED")
    private DeviceActionDispatchStatus status;

    @Schema(description = "是否被 device-service 接受并进入投递流程")
    private boolean accepted;

    @Schema(description = "下发方式（Websocket/Cache）", example = "Websocket")
    private String sendMethod;

    @Schema(description = "device-service 内部指令ID（设备侧只支持 Integer）", example = "123")
    private Integer queuedId;

    @Schema(description = "是否覆盖同 authorUrl 的旧指令")
    private boolean covered;

    @Schema(description = "错误信息（accepted=false 时有值）")
    private String errorMessage;
}

