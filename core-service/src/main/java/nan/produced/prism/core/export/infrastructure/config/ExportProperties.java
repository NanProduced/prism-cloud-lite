package nan.produced.prism.core.export.infrastructure.config;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import nan.produced.prism.core.export.api.ExportFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "prism.export")
public class ExportProperties {

    /**
     * Export 文件对象存储路径前缀（S3 key 的第一段）。
     */
    private String rootPrefix = "export";

    /**
     * Worker 临时目录（写 CSV/XLSX/JSON/Parquet 后再上传到 S3）。
     */
    private String tempDir;

    /**
     * 下载链接（presigned GET）的有效期（分钟）。
     */
    private int downloadUrlExpirationMinutes = 15;

    /**
     * Worker 并发（RabbitListener）。
     */
    private int workerConcurrency = 1;

    /**
     * Tier 级别允许的导出格式（用于创建阶段的 cheap 拦截）。
     * key: FREE/PRO
     */
    private Map<String, EnumSet<ExportFormat>> allowedFormatsByTier = Map.of(
            "FREE", EnumSet.of(ExportFormat.CSV, ExportFormat.JSON),
            "PRO", EnumSet.allOf(ExportFormat.class)
    );

    public EnumSet<ExportFormat> getAllowedFormatsOrDefault(String tier) {
        String normalized = normalizeTierOrFree(tier);
        EnumSet<ExportFormat> formats = allowedFormatsByTier != null ? allowedFormatsByTier.get(normalized) : null;
        if (formats == null && allowedFormatsByTier != null) {
            for (var e : allowedFormatsByTier.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(normalized)) {
                    formats = e.getValue();
                    break;
                }
            }
        }
        if (formats == null) {
            return "PRO".equals(normalized) ? EnumSet.allOf(ExportFormat.class) : EnumSet.of(ExportFormat.CSV, ExportFormat.JSON);
        }
        return formats;
    }

    /**
     * Tier 级别导出硬限制（用于保护服务器资源）。
     * key: FREE/PRO
     */
    private Map<String, TierLimits> limitsByTier = defaultLimits();

    public TierLimits getLimitsOrDefault(String tier) {
        String normalized = normalizeTierOrFree(tier);
        TierLimits limits = limitsByTier != null ? limitsByTier.get(normalized) : null;
        if (limits == null && limitsByTier != null) {
            for (var e : limitsByTier.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(normalized)) {
                    limits = e.getValue();
                    break;
                }
            }
        }
        if (limits == null) {
            return "PRO".equals(normalized) ? TierLimits.proDefaults() : TierLimits.freeDefaults();
        }
        return limits;
    }

    private String normalizeTierOrFree(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String upper = tier.trim().toUpperCase();
        return "PRO".equals(upper) ? "PRO" : "FREE";
    }

    private static Map<String, TierLimits> defaultLimits() {
        Map<String, TierLimits> map = new HashMap<>();
        map.put("FREE", TierLimits.freeDefaults());
        map.put("PRO", TierLimits.proDefaults());
        return map;
    }

    @Getter
    @Setter
    public static class TierLimits {
        /**
         * 时间范围最大跨度（天）。
         * -1 或 0 表示不限制
         */
        private int maxRangeDays;

        /**
         * 可选字段数量上限。
         * -1 或 0 表示不限制
         */
        private int maxFields;

        /**
         * 导出最大行数。
         * -1 或 0 表示不限制
         */
        private long maxRows;

        /**
         * 导出最大文件大小（字节）。
         * -1 或 0 表示不限制
         */
        private long maxBytes;

        /**
         * 单任务最大执行时长（秒）。
         * -1 或 0 表示不限制
         */
        private int maxRunSeconds;

        public static TierLimits freeDefaults() {
            TierLimits limits = new TierLimits();
            limits.maxRangeDays = 7;
            limits.maxFields = 20;
            limits.maxRows = 50_000;
            limits.maxBytes = 100L * 1024 * 1024;
            limits.maxRunSeconds = 180;
            return limits;
        }

        public static TierLimits proDefaults() {
            TierLimits limits = new TierLimits();
            limits.maxRangeDays = 365;
            limits.maxFields = 200;
            limits.maxRows = 2_000_000;
            limits.maxBytes = 2L * 1024 * 1024 * 1024;
            limits.maxRunSeconds = 1800;
            return limits;
        }
    }
}
