package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "用户存储空间配额（快速卡片）")
public record UserStorageQuotaView(
        @Schema(description = "订阅等级（FREE/PRO）", example = "FREE")
        String tier,
        @Schema(description = "存储上限（字节，-1 表示无限制）", nullable = true)
        Long quotaBytes,
        @Schema(description = "已用存储（字节）", example = "1024")
        long usedBytes,
        @Schema(description = "可用存储（字节）；当 quotaBytes 为 null/无限制 时为 null", nullable = true)
        Long availableBytes,
        @Schema(description = "使用比例（0-1）；当 quotaBytes 为空/<=0/无限制 时为 null", nullable = true)
        Double percent,
        @Schema(description = "使用量更新时间（来自 pcc_user_quota_usage.last_updated）", nullable = true)
        Instant usageUpdatedAt
) {
}

