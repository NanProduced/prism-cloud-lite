package nan.produced.prism.core.device.domain.command.body;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "传感器上报时间参数(单位都为秒/s)")
@Data
public class SensorReportTimeBody extends DeviceActionBodyBase {

    @NotNull
    @Schema(description = "gps上报时间间隔信息:0是关闭，大于0是打开并配置时间间隔（秒）,最小间隔：3秒", example = "30")
    @JsonProperty("gps.report.interval")
    private Integer gpsReportInterval;

    @NotNull
    @Schema(description = "传感器上报时间间隔信息:0是关闭，大于0是打开并配置时间间隔（秒）", example = "30")
    @JsonProperty("sensor.report.interval")
    private Integer sensorReportInterval;

    @NotNull
    @Schema(description = "误码率（接收卡数据）上报时间间隔信息:0是关闭，大于0是打开并配置时间间隔（秒）", example = "30")
    @JsonProperty("ber.report.interval")
    private Integer berReportInterval;

    @NotNull
    @Schema(description = "gps采样时间间隔信息:0是关闭，大于0是打开并配置时间间隔（秒），最小间隔：1秒", example = "30")
    @JsonProperty("gps.sample.interval")
    private Integer gpsSampleInterval;
}
