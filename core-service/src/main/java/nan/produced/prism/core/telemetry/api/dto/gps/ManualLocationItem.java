package nan.produced.prism.core.telemetry.api.dto.gps;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "手动设置的设备坐标")
public record ManualLocationItem(
    @Schema(description = "经度")
    double longitude,
    @Schema(description = "纬度")
    double latitude,
    @Schema(description = "更新时间（UTC）")
    Instant updatedAt
) {
}

