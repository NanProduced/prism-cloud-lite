package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端回复用户（邮件）")
public record AdminBugReportReplyRequest(
        @Schema(description = "邮件主题（可选）") String subject,
        @Schema(description = "回复内容（富文本 HTML）") String replyHtml
) {
}

