package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.UserQuotaFacade;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQuotaService implements UserQuotaFacade {

    private final UserQuotaUsageRepository userQuotaUsageRepository;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;
    private final UserQuotaSignalPublisher userQuotaSignalPublisher;

    @Override
    @Transactional
    public void consumeDevices(UUID userId, String tier, int count) {
        if (userId == null || count <= 0) {
            return;
        }

        ensureQuotaUsageExists(userId);

        String normalizedTier = normalizeTier(tier);
        Integer limit = subscriptionQuotaFacade.getQuota(normalizedTier).deviceLimit();
        if (limit != null && limit >= 0) {
            int updated = userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(userId, count, limit);
            if (updated == 0) {
                publishExceededBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_DEVICES, "count", limit, count);
                throw new BizException(ErrorCode.DEVICE_QUOTA_EXCEEDED);
            }
            publishUpdatedBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_DEVICES, "count");
            return;
        }

        userQuotaUsageRepository.incrementDeviceCount(userId, count);
        publishUpdatedBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_DEVICES, "count");
    }

    @Override
    @Transactional
    public void releaseDevices(UUID userId, int count) {
        if (userId == null || count <= 0) {
            return;
        }

        UserQuotaUsageEntity usage = userQuotaUsageRepository.findByUserId(userId)
                .orElse(null);
        if (usage == null) {
            return;
        }

        int current = usage.getDeviceCount() != null ? usage.getDeviceCount() : 0;
        usage.setDeviceCount(Math.max(0, current - count));
        userQuotaUsageRepository.save(usage);

        String tier = resolveTierFromContext();
        publishUpdatedBestEffort(userId, tier, UserQuotaSignalPublisher.RESOURCE_DEVICES, "count");
    }

    @Override
    @Transactional
    public void consumeCustomColumns(UUID userId, String tier, int count) {
        if (userId == null || count <= 0) {
            return;
        }

        ensureQuotaUsageExists(userId);

        String normalizedTier = normalizeTier(tier);
        Integer limit = subscriptionQuotaFacade.getQuota(normalizedTier).customColumnLimit();
        if (limit != null && limit >= 0) {
            int updated = userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(userId, count, limit);
            if (updated == 0) {
                publishExceededBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS, "count", limit, count);
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_QUOTA_EXCEEDED);
            }
            publishUpdatedBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS, "count");
            return;
        }

        userQuotaUsageRepository.incrementCustomColumnCount(userId, count);
        publishUpdatedBestEffort(userId, normalizedTier, UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS, "count");
    }

    @Override
    @Transactional
    public void releaseCustomColumns(UUID userId, int count) {
        if (userId == null || count <= 0) {
            return;
        }

        UserQuotaUsageEntity usage = userQuotaUsageRepository.findByUserId(userId)
                .orElse(null);
        if (usage == null) {
            return;
        }

        int current = usage.getCustomColumnCount() != null ? usage.getCustomColumnCount() : 0;
        usage.setCustomColumnCount(Math.max(0, current - count));
        userQuotaUsageRepository.save(usage);

        String tier = resolveTierFromContext();
        publishUpdatedBestEffort(userId, tier, UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS, "count");
    }

    @Override
    @Transactional
    public void syncProgramCount(UUID userId, int programCount) {
        if (userId == null) {
            return;
        }

        ensureQuotaUsageExists(userId);

        int normalized = Math.max(0, programCount);
        userQuotaUsageRepository.setProgramCount(userId, normalized);
    }

    private void ensureQuotaUsageExists(UUID userId) {
        if (userQuotaUsageRepository.findByUserId(userId).isPresent()) {
            return;
        }

        try {
            userQuotaUsageRepository.save(UserQuotaUsageEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .lastUpdated(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to create quota usage record for userId={}, error={}", userId, ex.getMessage());
        }
    }

    private void publishUpdatedBestEffort(UUID userId, String tier, String resource, String unit) {
        UserQuotaUsageEntity usage = userQuotaUsageRepository.findByUserId(userId).orElse(null);
        if (usage == null) {
            return;
        }

        long used = switch (resource) {
            case UserQuotaSignalPublisher.RESOURCE_DEVICES -> usage.getDeviceCount() != null ? usage.getDeviceCount() : 0L;
            case UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS -> usage.getCustomColumnCount() != null ? usage.getCustomColumnCount() : 0L;
            default -> 0L;
        };

        String normalizedTier = StringUtils.hasText(tier) ? normalizeTier(tier) : null;
        Long limitValue = resolveLimit(resource, normalizedTier);
        userQuotaSignalPublisher.publishQuotaUpdated(userId, normalizedTier, resource, unit, used, limitValue);
    }

    private void publishExceededBestEffort(UUID userId, String tier, String resource, String unit, Integer limit, int delta) {
        UserQuotaUsageEntity usage = userQuotaUsageRepository.findByUserId(userId).orElse(null);
        if (usage == null) {
            return;
        }

        long current = switch (resource) {
            case UserQuotaSignalPublisher.RESOURCE_DEVICES -> usage.getDeviceCount() != null ? usage.getDeviceCount() : 0L;
            case UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS -> usage.getCustomColumnCount() != null ? usage.getCustomColumnCount() : 0L;
            default -> 0L;
        };

        long attempted = current + Math.max(0L, delta);
        Long limitValue = limit != null ? limit.longValue() : resolveLimit(resource, tier);
        userQuotaSignalPublisher.publishQuotaExceeded(userId, normalizeTier(tier), resource, unit, attempted, limitValue);
    }

    private String normalizeTier(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String normalized = tier.trim().toUpperCase();
        return "PRO".equals(normalized) ? "PRO" : "FREE";
    }

    private String resolveTierFromContext() {
        if (!CloudAuthContext.hasAuthenticatedUser()) {
            return null;
        }
        try {
            return CloudAuthContext.getCurrentUser().tier();
        } catch (Exception ignore) {
            return null;
        }
    }

    private Long resolveLimit(String resource, String tier) {
        if (!StringUtils.hasText(tier)) {
            return null;
        }

        var quota = subscriptionQuotaFacade.getQuota(tier);
        if (UserQuotaSignalPublisher.RESOURCE_DEVICES.equals(resource)) {
            Integer limit = quota.deviceLimit();
            return limit != null ? limit.longValue() : null;
        }
        if (UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS.equals(resource)) {
            Integer limit = quota.customColumnLimit();
            return limit != null ? limit.longValue() : null;
        }
        return null;
    }
}
