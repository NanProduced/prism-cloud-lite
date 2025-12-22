package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "音量调节参数")
@Data
public class VolumeBody extends DeviceActionBodyBase {

    @NotNull
    @Min(0)
    @Max(15)
    @Schema(description = "音量值（0-15）", example = "13", minimum = "0", maximum = "15")
    private Integer musicvolume;
}
