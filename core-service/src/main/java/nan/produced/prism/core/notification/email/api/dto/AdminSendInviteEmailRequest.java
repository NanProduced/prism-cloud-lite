package nan.produced.prism.core.notification.email.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端发送体验邀请邮件")
public record AdminSendInviteEmailRequest(
        @Schema(description = "收件邮箱") String to,
        @Schema(description = "收件人称呼（可选）") String name,
        @Schema(description = "体验链接（可选）") String ctaUrl,
        @Schema(description = "补充说明（可选，富文本 HTML）") String noteHtml
) {
}

