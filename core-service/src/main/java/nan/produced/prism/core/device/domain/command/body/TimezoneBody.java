package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "时间/时区设置参数")
@Data
public class TimezoneBody extends DeviceActionBodyBase {

    @Min(0)
    @Max(1)
    @Schema(description = "NITZ同步时间开关: 0-关闭；1-开启", example = "1", minimum = "0", maximum = "1", allowableValues = {"0", "1"},
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer enableNitzTime;

    @Min(0)
    @Max(1)
    @Schema(description = "NITZ同步时区开关: 0-关闭；1-开启", example = "1", minimum = "0", maximum = "1", allowableValues = {"0", "1"},
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer enableNitzTimezone;

    @Min(0)
    @Max(1)
    @Schema(description = "自动时间开关: 0-关闭；1-开启", example = "1", minimum = "0", maximum = "1", allowableValues = {"0", "1"},
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer isautotime;

    @Min(0)
    @Max(1)
    @Schema(description = "自动时区开关: 0-关闭；1-开启", example = "1", minimum = "0", maximum = "1", allowableValues = {"0", "1"},
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer isautotimezone;

    @Schema(description = "时间(格式：yyyy-MM-dd HH:mm:ss)", example = "2026-09-23 08:04:34",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String time;

    @Schema(description = "时区偏移量(格式：10.0/-3.0),范围：范围：-12.0 到 +14.0", example = "10.0",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Float timezone;

    @Schema(description = "时区ID(格式：Asia/Shanghai)", example = "Asia/Shanghai",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String timezoneId;


}
