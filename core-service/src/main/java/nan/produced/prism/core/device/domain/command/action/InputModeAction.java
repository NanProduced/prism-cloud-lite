package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.InputModeBody;

@Schema(description = "信号源切换动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class InputModeAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "修改信号源操作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private InputModeBody body;

    public InputModeAction() {
        setType(DeviceActionType.INPUT_MODE);
    }

}
