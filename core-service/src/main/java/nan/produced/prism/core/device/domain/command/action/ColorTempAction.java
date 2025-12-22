package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.ColorTempBody;

@Schema(description = "修改色温动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class ColorTempAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "修改色温操作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private ColorTempBody body;

    public ColorTempAction() {
        setType(DeviceActionType.COLOR_TEMP);
    }


}
