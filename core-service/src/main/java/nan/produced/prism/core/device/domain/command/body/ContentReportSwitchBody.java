package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "素材/节目统计上报开关设置参数")
@Data
public class ContentReportSwitchBody extends DeviceActionBodyBase {

    @NotNull
    @Min(0)
    @Max(1)
    @Schema(description = "素材统计开关:0-关，1-开", example = "1", minimum = "0", maximum = "1")
    private Integer status;

    @NotNull
    @Min(0)
    @Max(1)
    @Schema(description = "节目统计开关:0-关，1-开", example = "1", minimum = "0", maximum = "1")
    private Integer programReportStatus;

}
