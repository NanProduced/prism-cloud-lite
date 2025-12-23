package nan.produced.prism.core.telemetry.api.dto;

/**
 * 设备在线会话统计（在查询窗口内，按区间与窗口的交集计算）。
 */
public record DeviceOnlineSessionStats(
        long sessionCount,
        long totalOnlineSeconds,
        double avgSessionSeconds,
        long maxSessionSeconds,
        long p95SessionSeconds
) {
}

