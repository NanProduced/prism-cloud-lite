package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@Schema(description = "删除指定节目参数（设备端要求固定 body：{\"command\":\"\"}）")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeleteDeviceVsnBody extends DeviceActionBodyBase {

    @Schema(description = "固定为空字符串", example = "")
    private String command = "";
}

