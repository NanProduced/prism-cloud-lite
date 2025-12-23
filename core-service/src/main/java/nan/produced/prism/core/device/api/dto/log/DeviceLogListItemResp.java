package nan.produced.prism.core.device.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "设备日志列表项")
public record DeviceLogListItemResp(
    @Schema(description = "日志ID")
    Long id,
    @Schema(description = "设备ID")
    Long deviceId,
    @Schema(description = "操作类型ID（operation_id）")
    Integer operationId,
    @Schema(description = "日志等级（0-7）")
    Integer level,
    @Schema(description = "描述")
    String description,
    @Schema(description = "设备上报时间（尽力解析 device_time，UTC）")
    OffsetDateTime reportTime,
    @Schema(description = "服务端接收时间（UTC）")
    OffsetDateTime createTime
) {
}

