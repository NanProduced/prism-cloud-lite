package nan.produced.prism.core.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStorageUsageService implements UserStorageUsageFacade {

    private final UserStorageUsageRepository userStorageUsageRepository;
    private final UserQuotaUsageRepository userQuotaUsageRepository;

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

        log.debug("Storage usage incremented: userId={}, sourceType={}, fileType={}, fileCount+={}, bytes+={}",
                userId, sourceType, fileType, fileCount, bytes);
    }
}
