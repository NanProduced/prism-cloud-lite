package nan.produced.prism.core.telemetry.api.dto;

/**
 * 设备离线间隔（gap）统计（在查询窗口内，按相邻会话间隔计算）。
 */
public record DeviceOfflineGapStats(
        long gapCount,
        long totalGapSeconds,
        double avgGapSeconds,
        long maxGapSeconds
) {
}

