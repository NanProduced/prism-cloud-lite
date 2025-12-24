package nan.produced.prism.core.message.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息中心配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.message-center")
public class MessageCenterProperties {

    /**
     * 消息保留与清理策略
     */
    private Retention retention = new Retention();

    /**
     * 查询默认行为配置
     */
    private QueryDefaults queryDefaults = new QueryDefaults();

    @Getter
    @Setter
    public static class Retention {

        /**
         * 消息保留天数
         */
        private int days = 60;

        /**
         * 清理任务 cron 表达式（默认：每日 UTC 03:40）
         */
        private String cleanupCron = "0 40 3 * * *";

        /**
         * cron 时区
         */
        private String cleanupZone = "UTC";
    }

    @Getter
    @Setter
    public static class QueryDefaults {

        private int maxPageSize = 100;

        private int defaultPageSize = 20;

        private int maxRecentLimit = 20;

        private int defaultRecentLimit = 5;

        /**
         * listMessages 默认查询时间范围（天）
         */
        private int defaultFromDays = 7;
    }
}

