package nan.produced.prism.core.telemetry.api.dto.sensor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "接收卡采样点（扁平化）")
public record ReceiveCardSampleItem(
    @Schema(description = "网口号")
    Integer netPortNum,
    @Schema(description = "接收卡编号")
    Integer receiveCardNum,
    @Schema(description = "误码率")
    Double bitErrorRate,
    @Schema(description = "温度")
    Integer temperature,
    @Schema(description = "湿度")
    Integer humidity,
    @Schema(description = "烟雾")
    Double smoke,
    @Schema(description = "时间点（UTC，serverTime）")
    Instant at
) {
}

