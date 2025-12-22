package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "电源控制参数（休眠、唤醒、重启）")
@Data
public class PowerBody extends DeviceActionBodyBase {

    @NotNull
    @Pattern(regexp = "^(sleep|wakeup|reboot)$", message = "指令必须是 sleep（休眠）, wakeup（唤醒） 或 reboot（重启） 其中之一")
    @Schema(description = "电源控制指令", example = "sleep", allowableValues = {"sleep", "wakeup", "reboot"})
    private String command;
}
