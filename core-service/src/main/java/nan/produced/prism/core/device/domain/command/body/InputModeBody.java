package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "信号源切换参数")
public class InputModeBody extends DeviceActionBodyBase {

    @NotNull
    @Pattern(regexp = "^(hdmi|dvi)$", message = "输入模式必须是 hdmi 或 dvi 其中之一")
    @Schema(description = "输入模式", example = "hdmi", allowableValues = {"hdmi", "dvi"})
    private String inputmode;
}
