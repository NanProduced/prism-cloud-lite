package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.LocaleBody;

@Schema(description = "地区/语言切换动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class LocaleAction extends DeviceActionBase {

    @Valid
    @NotNull
    @Schema(description = "地区/语言切换参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocaleBody body;

    public LocaleAction() {
        setType(DeviceActionType.LOCALE);
    }
}
