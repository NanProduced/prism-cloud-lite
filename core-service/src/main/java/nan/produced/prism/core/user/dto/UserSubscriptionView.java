package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "用户订阅信息")
public record UserSubscriptionView(
    @Schema(description = "订阅等级（FREE/PRO）", example = "FREE")
    String tier,
    @Schema(description = "订阅开始时间（PRO）", nullable = true)
    Instant startAt,
    @Schema(description = "订阅到期时间（PRO）", nullable = true)
    Instant endAt,
    @Schema(description = "是否拥有有效的 PRO 权益", example = "false")
    boolean proActive
) {
}

