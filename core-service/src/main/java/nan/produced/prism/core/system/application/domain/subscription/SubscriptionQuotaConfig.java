package nan.produced.prism.core.system.application.domain.subscription;

import lombok.Data;

/**
 * 对应platform_config表中的SUBSCRIPTION_QUOTA类型的配置
 *
 * @author Nan
 */
@Data
public class SubscriptionQuotaConfig {

    /**
     * 设备上限，-1 表示无限制
     */
    private Integer deviceLimit;

    /**
     * 存储上限，-1 表示无限制
     */
    private Long storageLimitBytes;

    /**
     * 节目上限，-1 表示无限制
     */
    private Integer programLimit;

    /**
     * 节目版本上限，-1 表示无限制
     */
    private Integer programVersionLimit;

}
