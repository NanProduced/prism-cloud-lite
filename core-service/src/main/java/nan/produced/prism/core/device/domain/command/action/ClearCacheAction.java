package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.ClearCacheBody;

@Schema(description = "清除缓存动作")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClearCacheAction extends DeviceActionBase {

    @Schema(description = "清除缓存操作参数-为空", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ClearCacheBody body;

    public ClearCacheAction() {
        setType(DeviceActionType.CLEAR_CACHE);
    }
}
