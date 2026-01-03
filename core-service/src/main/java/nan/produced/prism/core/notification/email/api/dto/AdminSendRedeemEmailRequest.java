package nan.produced.prism.core.notification.email.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "管理端发送 Pro 兑换码邮件（自动生成兑换码）")
public record AdminSendRedeemEmailRequest(
        @Schema(description = "收件邮箱") String to,
        @Schema(description = "订阅期限（天）") Integer durationDays,
        @Schema(description = "兑换码过期时间（可选）") Instant expiresAt,
        @Schema(description = "收件人称呼（可选）") String name
) {
}

