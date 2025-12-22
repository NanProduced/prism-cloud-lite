package nan.produced.prism.core.program.api.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.program.domain.schedule.ScheduleAuditAction;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "排程变更日志（Lite：仅用于用户查看排程历史）")
public class ScheduleAuditLogResp {

    @Schema(description = "日志ID（Long）")
    private Long id;

    @Schema(description = "用户ID")
    private UUID userId;

    @Schema(description = "排程ID")
    private UUID scheduleId;

    @Schema(description = "动作类型")
    private ScheduleAuditAction action;

    @Schema(description = "扩展信息 JSON（变更摘要）")
    private String details;

    @Schema(description = "时间")
    private OffsetDateTime createdAt;
}

