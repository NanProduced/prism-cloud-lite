package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.SensorReportTimeBody;

@Schema(description = "传感器上报时间设置动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class SensorReportTimeAction extends DeviceActionBase {

    @NotNull
    @Schema(description = "动作参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private SensorReportTimeBody body;

    public SensorReportTimeAction() {
        setType(DeviceActionType.SET_SENSOR_REPORT_TIME);
    }
}
