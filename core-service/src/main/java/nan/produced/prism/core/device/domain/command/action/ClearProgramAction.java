package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.ClearProgramBody;

@Schema(description = "清除设备节目动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClearProgramAction extends DeviceActionBase {

    @NotNull
    @Schema(description = "清除设备节目动作参数-为空", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ClearProgramBody body;

    public ClearProgramAction() {
        setType(DeviceActionType.CLEAR_DEVICE_PROGRAM);
    }
}
