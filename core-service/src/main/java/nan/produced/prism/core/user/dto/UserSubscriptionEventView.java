package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "订阅审计事件")
public record UserSubscriptionEventView(
    @Schema(description = "事件ID")
    Long id,
    @Schema(description = "事件类型", example = "REDEEM_CODE")
    String type,
    @Schema(description = "是否成功", example = "true")
    boolean success,
    @Schema(description = "兑换码（如有）", nullable = true)
    String code,
    @Schema(description = "扩展元数据（JSON 字符串）", nullable = true)
    String metadata,
    @Schema(description = "事件时间")
    Instant createdAt
) {
}

