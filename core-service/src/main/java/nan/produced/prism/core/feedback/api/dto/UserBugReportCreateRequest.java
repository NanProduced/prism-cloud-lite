package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "用户提交 bug/反馈请求")
public record UserBugReportCreateRequest(
        @Schema(description = "标题") String title,
        @Schema(description = "富文本内容（HTML）") String contentHtml,
        @Schema(description = "当前页面 URL（可选）") String pageUrl,
        @Schema(description = "联系邮箱（可选，便于回访）") String contactEmail,
        @Schema(description = "附件列表（截图等，可选）") List<BugReportAttachment> attachments
) {
}

