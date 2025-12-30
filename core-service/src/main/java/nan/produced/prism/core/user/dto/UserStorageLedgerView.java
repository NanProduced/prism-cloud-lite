package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "用户存储空间对账明细（全量：含媒体库/截图/导出/VSN 等）")
public record UserStorageLedgerView(
        @Schema(description = "订阅等级（FREE/PRO）", example = "FREE")
        String tier,
        @Schema(description = "总存储上限（字节，-1 表示无限制）", nullable = true)
        Long quotaBytes,
        @Schema(description = "总占用字节数（来自对账明细汇总）", example = "1024")
        long ledgerTotalBytes,
        @Schema(description = "总占用字节数（来自 pcc_user_quota_usage.storage_total_bytes）", example = "1024")
        long quotaUsedBytes,
        @Schema(description = "差值（quotaUsedBytes - ledgerTotalBytes），用于排查不一致", example = "0")
        long mismatchBytes,
        @Schema(description = "对账条目更新时间（来自 pcc_user_quota_usage.last_updated）", nullable = true)
        Instant usageUpdatedAt,
        @Schema(description = "按来源拆分的对账明细")
        List<UserStorageLedgerSourceView> sources
) {
}

