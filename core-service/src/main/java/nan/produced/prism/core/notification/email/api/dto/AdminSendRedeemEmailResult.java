package nan.produced.prism.core.notification.email.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "发送兑换码邮件结果")
public record AdminSendRedeemEmailResult(
        @Schema(description = "生成的兑换码") String code
) {
}

