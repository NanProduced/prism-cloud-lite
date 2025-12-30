package nan.produced.prism.core.telemetry.api.dto;

import java.time.Instant;
import nan.produced.prism.core.telemetry.api.dto.ResourceStatus;

/**
 * 设备在线时长汇总（范围聚合）。
 *
 * @param deviceId 设备ID
 * @param onlineSeconds 在线总时长（秒）
 */
public record DeviceOnlineTimeDeviceSummary(
        Long deviceId,
        long onlineSeconds,
        String deviceName,
        ResourceStatus status,
        Instant deletedAt
) {
}
