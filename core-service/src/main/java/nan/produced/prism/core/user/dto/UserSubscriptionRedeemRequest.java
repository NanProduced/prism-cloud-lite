package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "订阅兑换请求")
public record UserSubscriptionRedeemRequest(
    @Schema(description = "兑换码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PR7F2A3B4C5D6E8F")
    String code
) {
}

