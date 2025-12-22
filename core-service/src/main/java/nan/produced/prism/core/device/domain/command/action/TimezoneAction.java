package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.TimezoneBody;

@Schema(description = "时区/时间设置动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class TimezoneAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "动作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private TimezoneBody body;

    public TimezoneAction() {
        setType(DeviceActionType.TIMEZONE);
    }
}
