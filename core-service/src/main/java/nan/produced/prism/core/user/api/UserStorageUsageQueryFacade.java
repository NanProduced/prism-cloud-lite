package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户存储使用量查询门面（跨模块公开 API）。
 */
public interface UserStorageUsageQueryFacade {

    /**
     * 查询用户在指定来源下的存储使用量汇总。
     *
     * @param userId     用户ID
     * @param sourceType 来源类型
     */
    StorageUsageSummary getUsage(UUID userId, StorageSourceType sourceType);
}

