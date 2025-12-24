package nan.produced.prism.core.telemetry.api.dto.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "传感器指标序列查询结果")
public record SensorMetricSeriesResponse(
    @Schema(description = "设备ID")
    Long deviceId,
    @Schema(description = "查询起始时间（UTC）")
    Instant from,
    @Schema(description = "查询结束时间（UTC）")
    Instant to,
    @Schema(description = "序列列表")
    List<SensorMetricSeries> series
) {
}

