package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import nan.produced.prism.core.feedback.domain.BugReportStatus;

@Schema(description = "管理端 bug 列表项")
public record AdminBugReportListItemView(
        UUID id,
        BugReportStatus status,
        String title,
        String userPublicId,
        String userEmail,
        String contactEmail,
        String pageUrl,
        Instant createdAt
) {
}

