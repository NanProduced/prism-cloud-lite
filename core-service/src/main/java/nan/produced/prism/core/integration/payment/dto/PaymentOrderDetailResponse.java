package nan.produced.prism.core.integration.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentOrderDetailResponse(
    @Schema(description = "订单ID")
    String id,

    @Schema(description = "用户ID")
    String userId,

    @Schema(description = "本地订单号")
    String orderNo,

    @Schema(description = "Paddle交易ID")
    String externalOrderNo,

    @Schema(description = "订单金额")
    BigDecimal amount,

    @Schema(description = "货币类型")
    String currency,

    @Schema(description = "订单状态")
    String status,

    @Schema(description = "产品类型")
    String productType,

    @Schema(description = "Paddle Price ID")
    String priceId,

    @Schema(description = "Checkout URL")
    String checkoutUrl,

    @Schema(description = "创建时间")
    Instant createdAt,

    @Schema(description = "更新时间")
    Instant updatedAt
) {}
