package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.UserQuotaFacade;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQuotaService implements UserQuotaFacade {

    private final UserQuotaUsageRepository userQuotaUsageRepository;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;

    @Override
    @Transactional
    public void consumeCustomColumns(UUID userId, String tier, int count) {
        if (userId == null || count <= 0) {
            return;
        }

        ensureQuotaUsageExists(userId);

        Integer limit = subscriptionQuotaFacade.getQuota(tier).customColumnLimit();
        if (limit != null && limit >= 0) {
            int updated = userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(userId, count, limit);
            if (updated == 0) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_QUOTA_EXCEEDED);
            }
            return;
        }

        userQuotaUsageRepository.incrementCustomColumnCount(userId, count);
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
}

