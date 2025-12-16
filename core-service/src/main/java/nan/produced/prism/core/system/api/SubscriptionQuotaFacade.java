package nan.produced.prism.core.system.api;

/**
 * 订阅配额查询门面（跨模块公开 API）。
 */
public interface SubscriptionQuotaFacade {

    /**
     * 根据订阅层级获取配额配置。
     *
     * @param tier 订阅层级（例如：FREE/PRO），为空时按 FREE 处理
     */
    SubscriptionQuota getQuota(String tier);
}

