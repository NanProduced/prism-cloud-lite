package nan.produced.prism.core.program.application.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramQuotaSignalPublisher {

    public static final String RESOURCE_PROGRAMS = "programs";
    public static final String RESOURCE_PROGRAM_VERSIONS = "programVersions";

    private static final String TYPE_QUOTA_UPDATED = "quota.updated";
    private static final String TYPE_QUOTA_NEAR_LIMIT = "quota.near_limit";
    private static final String TYPE_QUOTA_EXCEEDED = "quota.exceeded";

    private final RabbitMessagePublisher rabbitMessagePublisher;
    private final StringRedisTemplate redisTemplate;

    @Value("${prism.sse.quota.updated.debounce-ms:2000}")
    private long updatedDebounceMs;

    @Value("${prism.sse.quota.warn.cooldown-ms:21600000}") // 6h
    private long warnCooldownMs;

    @Value("${prism.sse.quota.near-limit.percent:0.9}")
    private double nearLimitPercent;

    public void publishProgramsUpdated(UUID userId, String tier, long used, Integer limit) {
        publishQuotaUpdated(userId, tier, RESOURCE_PROGRAMS, "count", used, limit != null ? limit.longValue() : null, null);
    }

    public void publishProgramVersionsUpdated(UUID userId, String tier, UUID programId, long used, Integer limit) {
        publishQuotaUpdated(userId, tier, RESOURCE_PROGRAM_VERSIONS, "count", used, limit != null ? limit.longValue() : null, programId);
    }

    public void publishProgramsExceeded(UUID userId, String tier, long used, Integer limit) {
        publishQuotaExceeded(userId, tier, RESOURCE_PROGRAMS, "count", used, limit != null ? limit.longValue() : null, null);
    }

    public void publishProgramVersionsExceeded(UUID userId, String tier, UUID programId, long used, Integer limit) {
        publishQuotaExceeded(userId, tier, RESOURCE_PROGRAM_VERSIONS, "count", used, limit != null ? limit.longValue() : null, programId);
    }

    private void publishQuotaUpdated(UUID userId,
                                    String tier,
                                    String resource,
                                    String unit,
                                    long used,
                                    Long limit,
                                    UUID programId) {
        publishQuotaEventBestEffort(TYPE_QUOTA_UPDATED, userId, tier, resource, unit, used, limit, programId, updatedDebounceMs);

        Double percent = computePercent(used, limit);
        if (percent != null && percent >= nearLimitPercent && percent < 1.0d) {
            publishQuotaEventBestEffort(TYPE_QUOTA_NEAR_LIMIT, userId, tier, resource, unit, used, limit, programId, warnCooldownMs);
        }
    }

    private void publishQuotaExceeded(UUID userId,
                                     String tier,
                                     String resource,
                                     String unit,
                                     long used,
                                     Long limit,
                                     UUID programId) {
        publishQuotaEventBestEffort(TYPE_QUOTA_EXCEEDED, userId, tier, resource, unit, used, limit, programId, warnCooldownMs);
    }

    private void publishQuotaEventBestEffort(String type,
                                             UUID userId,
                                             String tier,
                                             String resource,
                                             String unit,
                                             long used,
                                             Long limit,
                                             UUID programId,
                                             long debounceMs) {
        if (userId == null || !StringUtils.hasText(type) || !StringUtils.hasText(resource)) {
            return;
        }

        try {
            if (!acquireToken(type, userId, resource, programId, debounceMs)) {
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("resource", resource);
            if (StringUtils.hasText(unit)) {
                data.put("unit", unit);
            }
            data.put("used", used);
            if (limit != null) {
                data.put("limit", limit);
                Double percent = computePercent(used, limit);
                if (percent != null) {
                    data.put("percent", percent);
                    data.put("remaining", Math.max(0L, limit - used));
                }
            }
            if (StringUtils.hasText(tier)) {
                data.put("tier", tier);
            }
            if (programId != null) {
                data.put("programId", programId);
            }

            String routingKey = switch (type) {
                case TYPE_QUOTA_NEAR_LIMIT -> MessagingConstants.RoutingKeys.NOTIFY_QUOTA_NEAR_LIMIT;
                case TYPE_QUOTA_EXCEEDED -> MessagingConstants.RoutingKeys.NOTIFY_QUOTA_EXCEEDED;
                default -> MessagingConstants.RoutingKeys.NOTIFY_QUOTA_UPDATED;
            };

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(type)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(userId)
                            .build())
                    .data(data)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(routingKey, message);
        } catch (Exception ex) {
            log.debug("program quota event publish failed (ignored): type={}, userId={}, resource={}", type, userId, resource, ex);
        }
    }

    private Double computePercent(long used, Long limit) {
        if (limit == null || limit <= 0L) {
            return null;
        }
        if (used <= 0L) {
            return 0d;
        }
        return Math.min(1d, (double) used / (double) limit);
    }

    private boolean acquireToken(String type, UUID userId, String resource, UUID programId, long ms) {
        if (ms <= 0L) {
            return true;
        }
        String key = "sse:" + type + ":" + userId + ":" + resource + (programId != null ? ":" + programId : "");
        Duration ttl = Duration.ofMillis(ms);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }
}

