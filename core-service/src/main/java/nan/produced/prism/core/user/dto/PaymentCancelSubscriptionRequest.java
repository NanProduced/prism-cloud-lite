package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentCancelSubscriptionRequest(
    @Schema(description = "是否立即生效", defaultValue = "false")
    Boolean effectiveImmediately
) {}
