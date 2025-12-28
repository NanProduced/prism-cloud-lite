package nan.produced.prism.core.user.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.user.api.StorageUsageItem;
import nan.produced.prism.core.user.api.StorageUsageSummary;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageQueryFacade;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserStorageUsageQueryService implements UserStorageUsageQueryFacade {

    private final UserStorageUsageRepository userStorageUsageRepository;

    @Override
    public StorageUsageSummary getUsage(UUID userId, StorageSourceType sourceType) {
        if (userId == null || sourceType == null) {
            return new StorageUsageSummary(0L, Collections.emptyList());
        }

        var rows = userStorageUsageRepository.findByUserIdAndSourceType(userId, sourceType);
        if (rows == null || rows.isEmpty()) {
            return new StorageUsageSummary(0L, Collections.emptyList());
        }

        var items = rows.stream()
                .map(row -> new StorageUsageItem(
                        row.getFileType(),
                        row.getFileCount() != null ? row.getFileCount() : 0,
                        row.getTotalBytes() != null ? Math.max(0L, row.getTotalBytes()) : 0L))
                .toList();

        long totalBytes = items.stream().mapToLong(StorageUsageItem::totalBytes).sum();
        return new StorageUsageSummary(totalBytes, items);
    }
}
