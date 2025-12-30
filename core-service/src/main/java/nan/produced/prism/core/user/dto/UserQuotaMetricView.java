package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "配额项（用量 + 上限）")
public record UserQuotaMetricView(
        @Schema(description = "资源类型", example = "storageBytes")
        String resource,
        @Schema(description = "单位", example = "bytes")
        String unit,
        @Schema(description = "已用")
        long used,
        @Schema(description = "上限（-1 表示无限制）", nullable = true)
        Long limit,
        @Schema(description = "使用比例（0-1）；当 limit 为空/<=0/无限制 时为 null", nullable = true)
        Double percent
) {
}

