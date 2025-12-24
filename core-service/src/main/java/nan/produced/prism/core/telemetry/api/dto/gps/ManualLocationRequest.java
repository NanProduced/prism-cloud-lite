package nan.produced.prism.core.telemetry.api.dto.gps;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "手动设置设备坐标请求")
public record ManualLocationRequest(
    @NotNull
    @Schema(description = "经度")
    Double longitude,
    @NotNull
    @Schema(description = "纬度")
    Double latitude
) {
}

