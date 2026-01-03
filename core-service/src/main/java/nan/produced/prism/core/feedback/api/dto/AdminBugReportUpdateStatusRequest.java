package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import nan.produced.prism.core.feedback.domain.BugReportStatus;

@Schema(description = "管理端更新 bug 状态/备注")
public record AdminBugReportUpdateStatusRequest(
        @Schema(description = "状态") BugReportStatus status,
        @Schema(description = "备注（可选）") String adminNote
) {
}

