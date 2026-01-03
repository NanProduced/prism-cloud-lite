package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.feedback.domain.BugReportStatus;

@Schema(description = "管理端 bug 详情")
public record AdminBugReportDetailView(
        UUID id,
        BugReportStatus status,
        String title,
        String contentHtml,
        String contentPlain,
        String pageUrl,
        String userAgent,
        String userPublicId,
        String userEmail,
        String userPhone,
        String contactEmail,
        List<BugReportAttachment> attachments,
        String adminNote,
        String replySubject,
        String replyHtml,
        Instant repliedAt,
        String repliedBy,
        Instant createdAt
) {
}

