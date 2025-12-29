package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.ScreenshotBody;

@Schema(description = "屏幕截图动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class ScreenshotAction extends DeviceActionBase {

    @Schema(description = "屏幕截图操作参数-为空", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ScreenshotBody body;

    public ScreenshotAction() {
        setType(DeviceActionType.SCREENSHOT);
    }
}
