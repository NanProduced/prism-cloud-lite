package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "当前用户配额使用情况总览（用于 Dashboard/Settings 卡片展示）")
public record UserQuotaOverviewView(
        @Schema(description = "订阅等级（FREE/PRO）", example = "FREE")
        String tier,
        @Schema(description = "配额项列表（设备/节目/自定义列/存储等）")
        List<UserQuotaMetricView> metrics,
        @Schema(description = "使用量更新时间（来自 pcc_user_quota_usage.last_updated）", nullable = true)
        Instant usageUpdatedAt
) {
}

