package nan.produced.prism.core.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStorageUsageService implements UserStorageUsageFacade {

    private final UserStorageUsageRepository userStorageUsageRepository;
    private final UserQuotaUsageRepository userQuotaUsageRepository;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;
    private final UserQuotaSignalPublisher userQuotaSignalPublisher;

    @Override
    @Transactional
    public void incrementUsage(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType,
            int fileCount,
            long bytes) {

        if (userId == null || sourceType == null || fileType == null) {
            return;
        }

        if (fileCount == 0 && bytes == 0L) {
            return;
        }

        int updated = userStorageUsageRepository.incrementUsage(
                userId,
                sourceType,
                fileType,
                fileCount,
                bytes);

        if (updated == 0) {
            var storageUsage = UserStorageUsageEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .sourceType(sourceType)
                    .fileType(fileType)
                    .fileCount(fileCount)
                    .totalBytes(bytes)
                    .build();
            userStorageUsageRepository.save(storageUsage);
        }

        if (bytes != 0L) {
            userQuotaUsageRepository.incrementStorageTotalBytes(userId, bytes);
        }

        publishStorageQuotaUpdatedBestEffort(userId);

        log.debug("Storage usage incremented: userId={}, sourceType={}, fileType={}, fileCount+={}, bytes+={}",
                userId, sourceType, fileType, fileCount, bytes);
    }

    @Override
    @Transactional
    public void decrementUsage(
            UUID userId,
            StorageSourceType sourceType,
            StorageFileType fileType,
            int fileCount,
            long bytes) {

        if (userId == null || sourceType == null || fileType == null) {
            return;
        }

        int normalizedFileCount = Math.max(0, fileCount);
        long normalizedBytes = Math.max(0L, bytes);
        if (normalizedFileCount == 0 && normalizedBytes == 0L) {
            return;
        }

        int updated = userStorageUsageRepository.decrementUsage(
                userId,
                sourceType,
                fileType,
                normalizedFileCount,
                normalizedBytes);

        if (updated == 0) {
            log.debug("Storage usage decrement skipped (no record): userId={}, sourceType={}, fileType={}, fileCount-={}, bytes-={}",
                    userId, sourceType, fileType, normalizedFileCount, normalizedBytes);
            return;
        }

        if (normalizedBytes != 0L) {
            userQuotaUsageRepository.incrementStorageTotalBytes(userId, -normalizedBytes);
        }

        publishStorageQuotaUpdatedBestEffort(userId);

        log.debug("Storage usage decremented: userId={}, sourceType={}, fileType={}, fileCount-={}, bytes-={}",
                userId, sourceType, fileType, normalizedFileCount, normalizedBytes);
    }

    private void publishStorageQuotaUpdatedBestEffort(UUID userId) {
        if (userId == null) {
            return;
        }

        UserQuotaUsageEntity usage = userQuotaUsageRepository.findByUserId(userId).orElse(null);
        if (usage == null) {
            return;
        }

        String tier = null;
        if (CloudAuthContext.hasAuthenticatedUser()) {
            try {
                tier = CloudAuthContext.getCurrentUser().tier();
            } catch (Exception ignore) {
                tier = null;
            }
        }

        Long limit = null;
        if (StringUtils.hasText(tier)) {
            limit = subscriptionQuotaFacade.getQuota(tier).storageLimitBytes();
        }

        long used = usage.getStorageTotalBytes() != null ? usage.getStorageTotalBytes() : 0L;
        userQuotaSignalPublisher.publishQuotaUpdated(
                userId,
                tier,
                UserQuotaSignalPublisher.RESOURCE_STORAGE_BYTES,
                "bytes",
                used,
                limit);
    }
}
