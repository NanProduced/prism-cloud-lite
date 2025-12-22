package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.program.domain.ProgramAuditAction;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目审计日志（状态页 Full Audit Trail）")
public class ProgramAuditLogResp {

    @Schema(description = "日志ID（Long）")
    private Long id;

    @Schema(description = "用户ID")
    private UUID userId;

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "动作类型")
    private ProgramAuditAction action;

    @Schema(description = "扩展信息 JSON（version/deviceCount 等）")
    private String details;

    @Schema(description = "时间")
    private OffsetDateTime createdAt;
}

