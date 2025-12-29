package nan.produced.prism.core.device.api.dto.commandlog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "设备指令日志列表项（Command Log）")
public record DeviceCommandLogListItemResp(
        @Schema(description = "日志ID")
        Long id,
        @Schema(description = "设备ID")
        Long deviceId,
        @Schema(description = "设备名称（冗余字段，便于前端展示与筛选）")
        String deviceName,
        @Schema(description = "操作ID（operationId == commandId）")
        String operationId,
        @Schema(description = "动作类型（DeviceActionType）")
        String actionType,
        @Schema(description = "追踪等级（DeviceActionTrackingLevel）")
        String trackingLevel,
        @Schema(description = "状态（DeviceCommandStatus）")
        String status,
        @Schema(description = "是否被 device-service 接收并进入投递流程")
        boolean accepted,
        @Schema(description = "是否被同类型后续指令覆盖")
        boolean covered,
        @Schema(description = "下发方式（Websocket/HTTP Cache 等，可为空）")
        String sendMethod,
        @Schema(description = "设备侧队列ID（queuedId，可为空）")
        Integer queuedId,
        @Schema(description = "错误信息（可为空）")
        String errorMessage,
        @Schema(description = "创建时间（UTC）")
        OffsetDateTime createdAt,
        @Schema(description = "更新时间（UTC）")
        OffsetDateTime updatedAt
) {
}
