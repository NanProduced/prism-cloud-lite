package nan.produced.prism.core.integration.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCreateOrderResponse(
    @Schema(description = "订单ID")
    String orderId,

    @Schema(description = "本地订单号")
    String orderNo,

    @Schema(description = "Paddle交易ID")
    String externalOrderNo,

    @Schema(description = "订单状态")
    String status,

    @Schema(description = "Checkout URL")
    String checkoutUrl
) {}
