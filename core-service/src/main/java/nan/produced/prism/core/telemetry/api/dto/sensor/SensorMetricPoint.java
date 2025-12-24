package nan.produced.prism.core.telemetry.api.dto.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "传感器指标点（折线图点）")
public record SensorMetricPoint(
    @Schema(description = "时间点（UTC）")
    Instant at,
    @Schema(description = "数值")
    Double value
) {
}

