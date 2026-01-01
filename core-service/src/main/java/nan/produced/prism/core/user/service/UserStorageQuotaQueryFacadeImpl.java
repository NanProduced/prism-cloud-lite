package nan.produced.prism.core.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.user.api.UserStorageQuotaQueryFacade;
import nan.produced.prism.core.user.api.UserStorageQuotaSnapshot;
import nan.produced.prism.core.user.dto.UserStorageQuotaView;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserStorageQuotaQueryFacadeImpl implements UserStorageQuotaQueryFacade {

    private final UserQuotaQueryService userQuotaQueryService;

    @Override
    public UserStorageQuotaSnapshot getStorageQuota(UUID userId, String tier) {
        UserStorageQuotaView view = userQuotaQueryService.getStorageQuota(userId, tier);
        if (view == null) {
            return new UserStorageQuotaSnapshot(null, null, 0L, null, null, null);
        }
        return new UserStorageQuotaSnapshot(
                view.tier(),
                view.quotaBytes(),
                view.usedBytes(),
                view.availableBytes(),
                view.percent(),
                view.usageUpdatedAt()
        );
    }
}

