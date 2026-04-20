package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentCreateOrderRequest(
    @Schema(description = "Paddle Price ID", requiredMode = Schema.RequiredMode.REQUIRED)
    String priceId,

    @Schema(description = "产品类型：SUBSCRIPTION/ONE_TIME", defaultValue = "SUBSCRIPTION")
    String productType,

    @Schema(description = "用户邮箱")
    String customerEmail,

    @Schema(description = "用户姓名")
    String customerName
) {}
