package nan.produced.prism.core.telemetry.api.dto;

import java.time.Instant;

/**
 * 在线会话明细（上线-下线区间）。
 *
 * @param sessionId 会话记录ID
 * @param onlineAt 记录的上线时间（UTC Instant）
 * @param offlineAt 记录的下线时间（UTC Instant）
 * @param effectiveOnlineAt 与查询窗口相交后的有效上线时间（UTC Instant）
 * @param effectiveOfflineAt 与查询窗口相交后的有效下线时间（UTC Instant）
 * @param onlineSecondsInRange 该会话在查询窗口内贡献的在线时长（秒）
 */
public record DeviceOnlineSessionItem(
        long sessionId,
        Instant onlineAt,
        Instant offlineAt,
        Instant effectiveOnlineAt,
        Instant effectiveOfflineAt,
        long onlineSecondsInRange
) {
}

