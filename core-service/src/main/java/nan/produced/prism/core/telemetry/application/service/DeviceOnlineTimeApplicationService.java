package nan.produced.prism.core.telemetry.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.telemetry.api.DeviceOnlineTimeFacade;
import nan.produced.prism.core.telemetry.api.dto.ActiveDeviceCountBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceConcurrencyBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOfflineGapStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionItem;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionStats;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeBucket;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineTimeDeviceSummary;
import nan.produced.prism.core.telemetry.api.dto.TimeBucketUnit;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceOnlineSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineTimeApplicationService implements DeviceOnlineTimeFacade {

    private static final int DEFAULT_SESSION_LIST_LIMIT = 200;
    private static final int MAX_SESSION_LIST_LIMIT = 1000;

    private final DeviceOnlineSessionRepository deviceOnlineSessionRepository;

    @Override
    @Transactional
    public boolean recordOnlineSession(UUID userId, Long deviceId, Instant onlineAt, Instant offlineAt, String traceId) {

        OffsetDateTime onlineAtUtc = onlineAt.atOffset(ZoneOffset.UTC);
        OffsetDateTime offlineAtUtc = offlineAt.atOffset(ZoneOffset.UTC);

        boolean inserted = deviceOnlineSessionRepository.insertIgnore(userId, deviceId, onlineAtUtc, offlineAtUtc);
        if (!inserted) {
            log.debug("OnlineTime - recordOnlineSession 已存在或插入失败（幂等忽略）: deviceId={}, onlineAt={}, offlineAt={}, traceId={}",
                    deviceId, onlineAt, offlineAt, traceId);
        }
        return inserted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceOnlineTimeDeviceSummary> summarizeDevices(UUID userId, Instant from, Instant to) {
        validateRange(userId, from, to);
        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceOnlineSessionRepository.OnlineSecondsByDeviceRow> rows =
                deviceOnlineSessionRepository.sumOnlineSecondsByDevice(userId, fromUtc, toUtc);

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> new DeviceOnlineTimeDeviceSummary(r.deviceId(), r.onlineSeconds()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceOnlineTimeBucket> getDeviceBuckets(
            UUID userId,
            Long deviceId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId 不能为空");
        }
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceOnlineSessionRepository.OnlineSecondsBucketRow> rows =
                deviceOnlineSessionRepository.sumOnlineSecondsByBucketForDevice(
                        userId,
                        deviceId,
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(r -> {
                    long bucketSeconds = Math.max(0L, r.bucketSeconds());
                    long onlineSeconds = Math.max(0L, r.onlineSeconds());
                    double uptimeRate = bucketSeconds > 0 ? (double) onlineSeconds / (double) bucketSeconds : 0d;
                    return new DeviceOnlineTimeBucket(
                            r.bucketStart().toInstant(),
                            r.bucketEnd().toInstant(),
                            bucketSeconds,
                            onlineSeconds,
                            uptimeRate
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveDeviceCountBucket> getActiveDeviceCountBuckets(
            UUID userId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceOnlineSessionRepository.ActiveDeviceCountBucketRow> rows =
                deviceOnlineSessionRepository.countActiveDevicesByBucket(
                        userId,
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(r -> new ActiveDeviceCountBucket(
                        r.bucketStart().toInstant(),
                        r.bucketEnd().toInstant(),
                        Math.max(0L, r.bucketSeconds()),
                        Math.max(0L, r.activeDevices())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceConcurrencyBucket> getConcurrencyBuckets(
            UUID userId,
            Instant from,
            Instant to,
            String tz,
            TimeBucketUnit bucketUnit) {

        validateRange(userId, from, to);
        if (bucketUnit == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "bucketUnit 不能为空");
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        List<DeviceOnlineSessionRepository.ConcurrencyBucketRow> rows =
                deviceOnlineSessionRepository.concurrencyByBucket(
                        userId,
                        fromUtc,
                        toUtc,
                        normalizeTz(tz),
                        bucketUnit.dateTruncUnit(),
                        bucketUnit.stepInterval()
                );

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(r -> {
                    long bucketSeconds = Math.max(0L, r.bucketSeconds());
                    long totalOnlineDeviceSeconds = Math.max(0L, r.totalOnlineDeviceSeconds());
                    double avgConcurrent = bucketSeconds > 0 ? (double) totalOnlineDeviceSeconds / (double) bucketSeconds : 0d;
                    long maxConcurrent = Math.max(0L, r.maxConcurrent());
                    return new DeviceConcurrencyBucket(
                            r.bucketStart().toInstant(),
                            r.bucketEnd().toInstant(),
                            bucketSeconds,
                            totalOnlineDeviceSeconds,
                            avgConcurrent,
                            maxConcurrent
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceOnlineSessionStats getDeviceSessionStats(UUID userId, Long deviceId, Instant from, Instant to) {
        validateRange(userId, from, to);
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId 不能为空");
        }
        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        DeviceOnlineSessionRepository.SessionStatsRow row =
                deviceOnlineSessionRepository.sessionStatsForDevice(userId, deviceId, fromUtc, toUtc);

        if (row == null) {
            return new DeviceOnlineSessionStats(0, 0, 0d, 0, 0);
        }
        return new DeviceOnlineSessionStats(
                Math.max(0L, row.sessionCount()),
                Math.max(0L, row.totalOnlineSeconds()),
                Math.max(0d, row.avgSessionSeconds()),
                Math.max(0L, row.maxSessionSeconds()),
                Math.max(0L, row.p95SessionSeconds())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceOfflineGapStats getDeviceOfflineGapStats(UUID userId, Long deviceId, Instant from, Instant to) {
        validateRange(userId, from, to);
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId 不能为空");
        }
        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);

        DeviceOnlineSessionRepository.OfflineGapStatsRow row =
                deviceOnlineSessionRepository.offlineGapStatsForDevice(userId, deviceId, fromUtc, toUtc);

        if (row == null) {
            return new DeviceOfflineGapStats(0, 0, 0d, 0);
        }
        return new DeviceOfflineGapStats(
                Math.max(0L, row.gapCount()),
                Math.max(0L, row.totalGapSeconds()),
                Math.max(0d, row.avgGapSeconds()),
                Math.max(0L, row.maxGapSeconds())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceOnlineSessionItem> listDeviceSessions(
            UUID userId,
            Long deviceId,
            Instant from,
            Instant to,
            Integer limit,
            Instant cursor) {

        validateRange(userId, from, to);
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId 不能为空");
        }
        int resolvedLimit = limit == null ? DEFAULT_SESSION_LIST_LIMIT : Math.max(1, limit);
        if (resolvedLimit > MAX_SESSION_LIST_LIMIT) {
            resolvedLimit = MAX_SESSION_LIST_LIMIT;
        }

        OffsetDateTime fromUtc = from.atOffset(ZoneOffset.UTC);
        OffsetDateTime toUtc = to.atOffset(ZoneOffset.UTC);
        OffsetDateTime cursorUtc = cursor != null ? cursor.atOffset(ZoneOffset.UTC) : null;

        List<DeviceOnlineSessionRepository.SessionItemRow> rows =
                deviceOnlineSessionRepository.listSessionsForDevice(userId, deviceId, fromUtc, toUtc, cursorUtc, resolvedLimit);

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(r -> new DeviceOnlineSessionItem(
                        r.sessionId(),
                        r.onlineAt().toInstant(),
                        r.offlineAt().toInstant(),
                        r.effectiveOnlineAt().toInstant(),
                        r.effectiveOfflineAt().toInstant(),
                        Math.max(0L, r.onlineSecondsInRange())
                ))
                .toList();
    }

    private void validateRange(UUID userId, Instant from, Instant to) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "userId 不能为空");
        }
        if (from == null || to == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "from/to 不能为空");
        }
        if (!from.isBefore(to)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "from 必须早于 to");
        }
    }

    private String normalizeTz(String tz) {
        if (tz == null || tz.isBlank()) {
            return "UTC";
        }
        String trimmed = tz.trim();
        if ("Z".equalsIgnoreCase(trimmed) || "UTC".equalsIgnoreCase(trimmed)) {
            return "UTC";
        }
        String normalizedOffset = tryNormalizeOffset(trimmed);
        return normalizedOffset != null ? normalizedOffset : trimmed;
    }

    private String tryNormalizeOffset(String tz) {
        String candidate = tz;
        if (candidate.regionMatches(true, 0, "UTC", 0, 3) && candidate.length() > 3) {
            candidate = candidate.substring(3);
        }
        candidate = candidate.trim();

        if (!(candidate.startsWith("+") || candidate.startsWith("-"))) {
            return null;
        }

        char sign = candidate.charAt(0);
        String rest = candidate.substring(1);
        Integer hour;
        Integer minute;

        if (rest.contains(":")) {
            String[] parts = rest.split(":", -1);
            if (parts.length != 2) {
                return null;
            }
            hour = parseInt(parts[0]);
            minute = parseInt(parts[1]);
        } else if (rest.length() == 4) {
            hour = parseInt(rest.substring(0, 2));
            minute = parseInt(rest.substring(2, 4));
        } else {
            hour = parseInt(rest);
            minute = 0;
        }

        if (hour == null || minute == null) {
            return null;
        }

        try {
            return ZoneOffset.ofHoursMinutes(sign == '-' ? -hour : hour, sign == '-' ? -minute : minute).getId();
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
