package nan.produced.prism.core.telemetry.api.dto.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "传感器指标序列（同一条折线）")
public record SensorMetricSeries(
    @Schema(description = "数据来源类型（DEVICE_SENSOR/M2_SENSOR）")
    String sourceType,
    @Schema(description = "业务数据类型（reportType）")
    String reportType,
    @Schema(description = "指标键（metricKey）")
    String metricKey,
    @Schema(description = "点列表（按时间升序）")
    List<SensorMetricPoint> points
) {
}

