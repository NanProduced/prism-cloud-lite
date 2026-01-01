package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户存储配额查询（跨模块公开 API）。
 */
public interface UserStorageQuotaQueryFacade {

    UserStorageQuotaSnapshot getStorageQuota(UUID userId, String tier);
}

