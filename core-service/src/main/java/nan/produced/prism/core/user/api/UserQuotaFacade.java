package nan.produced.prism.core.user.api;

import java.util.UUID;

/**
 * 用户配额使用量门面（跨模块公开 API）。
 *
 * <p>说明：配额的来源由 system 模块维护，使用量由 user 模块落库与维护。</p>
 */
public interface UserQuotaFacade {

    /**
     * 扣减设备可用额度（上云设备数量）。
     *
     * @param userId  用户ID
     * @param tier    订阅层级（FREE/PRO）
     * @param count   本次新增的设备数量（必须 &gt; 0）
     */
    void consumeDevices(UUID userId, String tier, int count);

    /**
     * 释放设备额度（删除设备时调用）。
     *
     * @param userId 用户ID
     * @param count  本次释放的数量（必须 &gt; 0）
     */
    void releaseDevices(UUID userId, int count);

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

    /**
     * 同步节目（Program）使用量。
     *
     * <p>
     * 说明：节目配额的校验与事件推送由 program 模块负责；user 模块仅落库一个派生的 {@code program_count}
     * 供前端/报表展示使用。为避免存量数据不一致，这里采用「直接 set 为真实 count」的方式同步，而不是 +1/-1。
     * </p>
     *
     * @param userId       用户ID
     * @param programCount 当前用户节目总数（&gt;= 0）
     */
    void syncProgramCount(UUID userId, int programCount);
}
