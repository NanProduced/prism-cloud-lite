package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.BrightnessBody;

@Schema(description = "亮度调节动作")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class BrightnessAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "动作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private BrightnessBody body;

    public BrightnessAction() {
        setType(DeviceActionType.BRIGHTNESS);
    }
}
