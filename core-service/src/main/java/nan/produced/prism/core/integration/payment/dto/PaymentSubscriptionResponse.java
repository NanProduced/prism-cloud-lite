package nan.produced.prism.core.integration.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PaymentSubscriptionResponse(
    @Schema(description = "订阅ID")
    String id,

    @Schema(description = "用户ID")
    String userId,

    @Schema(description = "Paddle订阅ID")
    String externalSubscriptionId,

    @Schema(description = "订阅等级")
    String tier,

    @Schema(description = "订阅状态")
    String status,

    @Schema(description = "当前周期开始时间")
    Instant currentPeriodStart,

    @Schema(description = "当前周期结束时间")
    Instant currentPeriodEnd,

    @Schema(description = "是否在周期结束时取消")
    Boolean cancelAtPeriodEnd,

    @Schema(description = "创建时间")
    Instant createdAt,

    @Schema(description = "更新时间")
    Instant updatedAt
) {}
