package nan.produced.prism.core.telemetry.api.dto;

/**
 * 时间分桶粒度（用于在线时长/并发/活跃数等聚合查询）。
 */
public enum TimeBucketUnit {
    HOUR("hour", "1 hour"),
    DAY("day", "1 day"),
    WEEK("week", "1 week"),
    MONTH("month", "1 month");

    private final String dateTruncUnit;
    private final String stepInterval;

    TimeBucketUnit(String dateTruncUnit, String stepInterval) {
        this.dateTruncUnit = dateTruncUnit;
        this.stepInterval = stepInterval;
    }

    public String dateTruncUnit() {
        return dateTruncUnit;
    }

    public String stepInterval() {
        return stepInterval;
    }
}

