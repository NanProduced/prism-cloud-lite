package nan.produced.prism.core.telemetry.api.dto.gps;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "设备位置快照（包含上报坐标与手动坐标）")
public record GpsDeviceLocationItem(
    @Schema(description = "设备ID")
    Long deviceId,
    @Schema(description = "设备上报坐标（最新一条）")
    GpsPointItem reported,
    @Schema(description = "手动坐标（若存在）")
    ManualLocationItem manual
) {
}

