package nan.produced.prism.core.telemetry.api.dto.gps;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "GPS 点位")
public record GpsPointItem(
    @Schema(description = "经度")
    double longitude,
    @Schema(description = "纬度")
    double latitude,
    @Schema(description = "精度（米）")
    Float accuracy,
    @Schema(description = "海拔")
    Float altitude,
    @Schema(description = "速度")
    Float speed,
    @Schema(description = "方向")
    Double direct,
    @Schema(description = "卫星数")
    Integer satellites,
    @Schema(description = "时间点（UTC，serverTime）")
    Instant at
) {
}

