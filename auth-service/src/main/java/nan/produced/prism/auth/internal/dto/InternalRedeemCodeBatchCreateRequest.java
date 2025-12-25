package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "InternalRedeemCodeBatchCreateRequest", description = "批量创建兑换码（内部接口）")
public record InternalRedeemCodeBatchCreateRequest(
    @Schema(description = "订阅等级（目前仅支持 PRO）", example = "PRO") String tier,
    @Schema(description = "权益时长（天）", example = "30") Integer durationDays,
    @Schema(description = "生成数量（默认 1，最大 500）", example = "10") Integer count,
    @Schema(description = "过期时间（可选）", nullable = true) Instant expiresAt
) {
}

