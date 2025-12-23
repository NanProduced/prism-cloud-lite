package nan.produced.prism.core.device.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "设备日志操作类型字典项")
public record DeviceLogTypeResp(
    @Schema(description = "operation_id")
    Integer id,
    @Schema(description = "operation 文案（英文）")
    String operation,
    @Schema(description = "log_type")
    String type,
    @Schema(description = "subType（拼接 subtype1-3）")
    String subType
) {
}

