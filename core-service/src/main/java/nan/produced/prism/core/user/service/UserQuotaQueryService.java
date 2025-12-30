package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.dto.UserQuotaMetricView;
import nan.produced.prism.core.user.dto.UserQuotaOverviewView;
import nan.produced.prism.core.user.dto.UserStorageLedgerItemView;
import nan.produced.prism.core.user.dto.UserStorageLedgerSourceView;
import nan.produced.prism.core.user.dto.UserStorageLedgerView;
import nan.produced.prism.core.user.dto.UserStorageQuotaView;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserQuotaQueryService {

    private final UserQuotaUsageRepository userQuotaUsageRepository;
    private final UserStorageUsageRepository userStorageUsageRepository;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;

    @Transactional(readOnly = true)
    public UserStorageQuotaView getStorageQuota(UUID userId, String tier) {
        String normalizedTier = normalizeTier(tier);
        SubscriptionQuota quota = subscriptionQuotaFacade.getQuota(normalizedTier);
        Long limitBytes = quota != null ? quota.storageLimitBytes() : null;

        UserQuotaUsageEntity usage = userId != null ? userQuotaUsageRepository.findByUserId(userId).orElse(null) : null;
        long usedBytes = usage != null && usage.getStorageTotalBytes() != null ? Math.max(0L, usage.getStorageTotalBytes()) : 0L;
        Instant updatedAt = usage != null ? usage.getLastUpdated() : null;

        if (usage == null && userId != null) {
            long ledgerTotal = computeLedgerTotalBytes(userId);
            usedBytes = ledgerTotal;
        }

        Long available = computeAvailableBytes(limitBytes, usedBytes);
        Double percent = computePercent(limitBytes, usedBytes);
        return new UserStorageQuotaView(normalizedTier, limitBytes, usedBytes, available, percent, updatedAt);
    }

    @Transactional(readOnly = true)
    public UserQuotaOverviewView getQuotaOverview(UUID userId, String tier) {
        String normalizedTier = normalizeTier(tier);
        SubscriptionQuota quota = subscriptionQuotaFacade.getQuota(normalizedTier);

        UserQuotaUsageEntity usage = userId != null ? userQuotaUsageRepository.findByUserId(userId).orElse(null) : null;
        int deviceCount = usage != null && usage.getDeviceCount() != null ? Math.max(0, usage.getDeviceCount()) : 0;
        int programCount = usage != null && usage.getProgramCount() != null ? Math.max(0, usage.getProgramCount()) : 0;
        int customColumnCount = usage != null && usage.getCustomColumnCount() != null ? Math.max(0, usage.getCustomColumnCount()) : 0;
        long storageBytes = usage != null && usage.getStorageTotalBytes() != null ? Math.max(0L, usage.getStorageTotalBytes()) : 0L;
        Instant updatedAt = usage != null ? usage.getLastUpdated() : null;

        List<UserQuotaMetricView> metrics = new ArrayList<>();
        metrics.add(toMetric("devices", "count", deviceCount, quota != null ? quota.deviceLimit() : null));
        metrics.add(toMetric("programs", "count", programCount, quota != null ? quota.programLimit() : null));
        metrics.add(toMetric("customColumns", "count", customColumnCount, quota != null ? quota.customColumnLimit() : null));
        metrics.add(toMetric("storageBytes", "bytes", storageBytes, quota != null ? quota.storageLimitBytes() : null));

        // programVersionLimit is a per-program limit (no user-level used value).
        Integer versionLimit = quota != null ? quota.programVersionLimit() : null;
        metrics.add(new UserQuotaMetricView(
                "programVersionsPerProgram",
                "count",
                0L,
                versionLimit != null ? versionLimit.longValue() : null,
                null
        ));

        return new UserQuotaOverviewView(normalizedTier, metrics, updatedAt);
    }

    @Transactional(readOnly = true)
    public UserStorageLedgerView getStorageLedger(UUID userId, String tier) {
        String normalizedTier = normalizeTier(tier);
        SubscriptionQuota quota = subscriptionQuotaFacade.getQuota(normalizedTier);
        Long limitBytes = quota != null ? quota.storageLimitBytes() : null;

        UserQuotaUsageEntity usage = userId != null ? userQuotaUsageRepository.findByUserId(userId).orElse(null) : null;
        long quotaUsedBytes = usage != null && usage.getStorageTotalBytes() != null ? Math.max(0L, usage.getStorageTotalBytes()) : 0L;
        Instant usageUpdatedAt = usage != null ? usage.getLastUpdated() : null;

        List<UserStorageUsageEntity> rows = userId != null ? userStorageUsageRepository.findByUserId(userId) : List.of();
        Map<StorageSourceType, List<UserStorageUsageEntity>> bySource = new EnumMap<>(StorageSourceType.class);
        for (UserStorageUsageEntity row : rows) {
            if (row == null || row.getSourceType() == null || row.getFileType() == null) {
                continue;
            }
            bySource.computeIfAbsent(row.getSourceType(), ignored -> new ArrayList<>()).add(row);
        }

        List<UserStorageLedgerSourceView> sources = new ArrayList<>();
        long ledgerTotalBytes = 0L;
        for (Map.Entry<StorageSourceType, List<UserStorageUsageEntity>> entry : bySource.entrySet()) {
            StorageSourceType sourceType = entry.getKey();
            List<UserStorageUsageEntity> items = entry.getValue();
            if (sourceType == null || items == null || items.isEmpty()) {
                continue;
            }

            items.sort(Comparator.comparing(UserStorageUsageEntity::getFileType, Comparator.nullsLast(Comparator.naturalOrder())));

            List<UserStorageLedgerItemView> ledgerItems = new ArrayList<>();
            long sourceBytes = 0L;
            int sourceCount = 0;
            for (UserStorageUsageEntity item : items) {
                StorageFileType fileType = item.getFileType();
                int count = item.getFileCount() != null ? Math.max(0, item.getFileCount()) : 0;
                long bytes = item.getTotalBytes() != null ? Math.max(0L, item.getTotalBytes()) : 0L;
                sourceBytes += bytes;
                sourceCount += count;
                ledgerItems.add(new UserStorageLedgerItemView(fileType, count, bytes, item.getUpdatedAt()));
            }

            ledgerTotalBytes += sourceBytes;
            sources.add(new UserStorageLedgerSourceView(sourceType, sourceBytes, sourceCount, ledgerItems));
        }

        sources.sort(Comparator.comparing(UserStorageLedgerSourceView::sourceType, Comparator.nullsLast(Comparator.naturalOrder())));

        long mismatch = quotaUsedBytes - ledgerTotalBytes;
        return new UserStorageLedgerView(
                normalizedTier,
                limitBytes,
                ledgerTotalBytes,
                quotaUsedBytes,
                mismatch,
                usageUpdatedAt,
                sources
        );
    }

    private long computeLedgerTotalBytes(UUID userId) {
        if (userId == null) {
            return 0L;
        }
        List<UserStorageUsageEntity> rows = userStorageUsageRepository.findByUserId(userId);
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (UserStorageUsageEntity row : rows) {
            if (row == null || row.getTotalBytes() == null) {
                continue;
            }
            sum += Math.max(0L, row.getTotalBytes());
        }
        return sum;
    }

    private static UserQuotaMetricView toMetric(String resource, String unit, long used, Integer limit) {
        Long l = limit != null ? limit.longValue() : null;
        return toMetric(resource, unit, used, l);
    }

    private static UserQuotaMetricView toMetric(String resource, String unit, long used, Long limit) {
        long normalizedUsed = Math.max(0L, used);
        Double percent = computePercent(limit, normalizedUsed);
        return new UserQuotaMetricView(resource, unit, normalizedUsed, limit, percent);
    }

    private static Long computeAvailableBytes(Long limitBytes, long usedBytes) {
        if (limitBytes == null || limitBytes < 0) {
            return null;
        }
        long available = limitBytes - Math.max(0L, usedBytes);
        return Math.max(0L, available);
    }

    private static Double computePercent(Long limit, long used) {
        if (limit == null) {
            return null;
        }
        if (limit <= 0 || limit < 0) {
            return null;
        }
        return Math.min(1d, Math.max(0d, used / (double) limit));
    }

    private static String normalizeTier(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String normalized = tier.trim().toUpperCase();
        return "PRO".equals(normalized) ? "PRO" : "FREE";
    }
}

