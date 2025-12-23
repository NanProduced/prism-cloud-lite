package nan.produced.prism.core.telemetry.api;

import nan.produced.prism.core.telemetry.api.dto.ActiveDeviceCountBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceConcurrencyBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOfflineGapStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionItem;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeDeviceSummary;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 设备在线时长（上线-下线区间）统计能力
 * <p>
 * - 写入：device-service 离线时推送 (onlineTime/offlineTime) 到 core-service 后落库
 * - 查询：面向 SPA 的范围聚合/分桶聚合/会话明细/稳定性等统计能力
 */
public interface DeviceOnlineTimeFacade {

    /**
     * 记录一段设备在线区间（上线-下线）。
     * <p>
     * 该方法应具备幂等性：重复上报同一 (deviceId, onlineAt, offlineAt) 不应导致重复记录。
     */
    boolean recordOnlineSession(UUID userId, Long deviceId, Instant onlineAt, Instant offlineAt, String traceId);

    /**
     * 查询用户范围内，各设备在时间窗内的在线总时长（秒）。
     */
    List<DeviceOnlineTimeDeviceSummary> summarizeDevices(UUID userId, Instant from, Instant to);

    /**
     * 查询单设备在时间窗内按桶聚合的在线时长（支持时区分桶）。
     */
    List<DeviceOnlineTimeBucket> getDeviceBuckets(
            UUID userId,
            Long deviceId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    /**
     * 查询用户范围内按桶聚合的活跃设备数（桶内在线>0即视为活跃）。
     */
    List<ActiveDeviceCountBucket> getActiveDeviceCountBuckets(
            UUID userId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    /**
     * 查询用户范围内按桶聚合的并发在线统计（平均并发、最大并发）。
     */
    List<DeviceConcurrencyBucket> getConcurrencyBuckets(
            UUID userId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit);

    /**
     * 查询单设备在时间窗内的会话统计（会话数、平均/最大/P95 会话时长等）。
     */
    DeviceOnlineSessionStats getDeviceSessionStats(UUID userId, Long deviceId, Instant from, Instant to);

    /**
     * 查询单设备在时间窗内的离线间隔统计（掉线间隔）。
     */
    DeviceOfflineGapStats getDeviceOfflineGapStats(UUID userId, Long deviceId, Instant from, Instant to);

    /**
     * 查询单设备在时间窗内的会话明细（按 onlineAt 倒序）。
     */
    List<DeviceOnlineSessionItem> listDeviceSessions(
            UUID userId,
            Long deviceId,
            Instant from,
            Instant to,
            Integer limit,
            Instant cursor);
}

