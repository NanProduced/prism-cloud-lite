package nan.produced.prism.core.telemetry.application.support;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Telemetry 查询时间窗工具：统一按 UTC clamp 到“最近 N 天”，并给出默认查询窗口。
 */
public final class TelemetryQueryWindow {

    private TelemetryQueryWindow() {
    }

    public record TimeWindow(OffsetDateTime from, OffsetDateTime to) {
    }

    public static TimeWindow clamp(Instant from,
                                   Instant to,
                                   int retentionDays,
                                   int defaultRangeDays) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusDays(Math.max(0, retentionDays));

        OffsetDateTime safeTo = to != null ? to.atOffset(ZoneOffset.UTC) : now;
        if (safeTo.isAfter(now)) {
            safeTo = now;
        }

        OffsetDateTime safeFrom = from != null ? from.atOffset(ZoneOffset.UTC) : safeTo.minusDays(Math.max(1, defaultRangeDays));
        if (safeFrom.isBefore(cutoff)) {
            safeFrom = cutoff;
        }

        return new TimeWindow(safeFrom, safeTo);
    }
}

