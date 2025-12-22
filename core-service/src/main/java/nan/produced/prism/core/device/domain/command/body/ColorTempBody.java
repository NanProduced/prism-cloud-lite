package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "色温调节参数")
@Data
public class ColorTempBody extends DeviceActionBodyBase {

    @NotNull
    @Min(2000)
    @Max(10000)
    @Schema(description = "色温值（2000-10000）", example = "5000", minimum = "2000", maximum = "10000")
    private Integer colortemp;
}
