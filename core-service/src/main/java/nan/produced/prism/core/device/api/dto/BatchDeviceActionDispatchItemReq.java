package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;

@Schema(description = "批量下发设备动作 - 单个设备项")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeviceActionDispatchItemReq {

    @NotNull
    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    private Long deviceId;

    @Valid
    @NotNull
    @Schema(description = "动作请求（包含 type/body/ttlMinutes 等）", requiredMode = Schema.RequiredMode.REQUIRED)
    private DeviceActionBase action;
}

