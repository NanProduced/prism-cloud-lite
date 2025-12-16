package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户配额使用量门面（跨模块公开 API）。
 *
 * <p>说明：配额的来源由 system 模块维护，使用量由 user 模块落库与维护。</p>
 */
public interface UserQuotaFacade {

    /**
     * 扣减自定义列（设备自定义字段定义）的可用额度。
     *
     * @param userId  用户ID
     * @param tier    订阅层级（FREE/PRO）
     * @param count   本次新增的自定义列数量（必须 &gt; 0）
     */
    void consumeCustomColumns(UUID userId, String tier, int count);

    /**
     * 释放自定义列额度（删除自定义字段定义时调用）。
     *
     * @param userId 用户ID
     * @param count  本次释放的数量（必须 &gt; 0）
     */
    void releaseCustomColumns(UUID userId, int count);
}

