package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.ContentReportSwitchBody;

@Schema(description = "素材/节目统计上报开关设置动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReportSwitchAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "素材/节目统计上报开关参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private ContentReportSwitchBody body;

    public ContentReportSwitchAction() {
        setType(DeviceActionType.CONTENT_REPORT_SWITCH);
    }
}
