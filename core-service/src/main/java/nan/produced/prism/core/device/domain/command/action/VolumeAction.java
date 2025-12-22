package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.VolumeBody;

@Schema(description = "音量调节动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class VolumeAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "修改音量操作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private VolumeBody body;

    public VolumeAction() {
        setType(DeviceActionType.VOLUME);
    }
}
