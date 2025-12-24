package nan.produced.prism.core.telemetry.api.dto.gps;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "热力图网格聚合单元（经纬度桶 + 计数）")
public record GpsHeatmapCellItem(
    @Schema(description = "经度桶")
    double longitude,
    @Schema(description = "纬度桶")
    double latitude,
    @Schema(description = "点数量")
    long count
) {
}

