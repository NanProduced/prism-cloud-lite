package nan.produced.prism.core.device.api.dto.commandlog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "设备指令日志分页结果（按创建时间倒序）")
public record DeviceCommandLogPageResp(
        @Schema(description = "日志列表")
        List<DeviceCommandLogListItemResp> items,
        @Schema(description = "页码（从 0 开始）")
        int page,
        @Schema(description = "每页大小")
        int size,
        @Schema(description = "总条数")
        long total
) {
}

