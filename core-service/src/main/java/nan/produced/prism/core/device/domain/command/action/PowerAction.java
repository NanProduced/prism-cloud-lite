package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.PowerBody;

@Schema(description = "电源控制操作")
@Data
@EqualsAndHashCode(callSuper = true)
public class PowerAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "动作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private PowerBody body;

    public PowerAction() {
        setType(DeviceActionType.POWER);
    }
}
