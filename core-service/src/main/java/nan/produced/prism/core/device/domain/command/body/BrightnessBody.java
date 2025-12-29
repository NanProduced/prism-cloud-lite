package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "亮度调节参数")
@Data
public class BrightnessBody extends DeviceActionBodyBase {

    @NotNull
    @Min(0)
    @Max(255)
    @Schema(description = "亮度值（0-255，设备原始范围；UI 建议换算为 0-100% 展示）", example = "128", minimum = "0", maximum = "255")
    private Integer brightness;

}
