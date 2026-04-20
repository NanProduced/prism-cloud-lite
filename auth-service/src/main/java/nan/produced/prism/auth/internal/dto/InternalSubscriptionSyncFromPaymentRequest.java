package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record InternalSubscriptionSyncFromPaymentRequest(
    @Schema(description = "用户ID")
    String userId,

    @Schema(description = "Paddle订阅ID")
    String externalSubscriptionId,

    @Schema(description = "订阅等级：PRO/FREE")
    String tier,

    @Schema(description = "订阅开始时间")
    Instant startAt,

    @Schema(description = "订阅结束时间")
    Instant endAt,

    @Schema(description = "订阅状态：ACTIVE/CANCELED/PAUSED/PAST_DUE")
    String status,

    @Schema(description = "元数据")
    Map<String, Object> metadata
) {}
