package nan.produced.prism.core.device.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLastReportTimeWriteBehindBuffer {

    private static final long MAX_BUFFERED_DEVICES = 200_000;
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);

    private final DeviceRepository deviceRepository;

    private final Cache<Long, Instant> lastReportTimeBuffer = Caffeine.newBuilder()
            .maximumSize(MAX_BUFFERED_DEVICES)
            .expireAfterWrite(EXPIRE_AFTER_WRITE)
            .build();

    public void record(Long deviceId, Instant occurredAt) {
        if (deviceId == null) {
            return;
        }
        Instant safeOccurredAt = occurredAt != null ? occurredAt : Instant.now();
        lastReportTimeBuffer.asMap().merge(deviceId, safeOccurredAt, (oldVal, newVal) -> oldVal.isAfter(newVal) ? oldVal : newVal);
    }

    public int bufferedSize() {
        return lastReportTimeBuffer.asMap().size();
    }

    /**
     * 将缓冲的 last_report_time 写回 DB（best-effort）。
     *
     * @return 本次 flush 实际更新的设备数量
     */
    public int flushOnce() {
        int updated = 0;
        for (Map.Entry<Long, Instant> entry : lastReportTimeBuffer.asMap().entrySet()) {
            Long deviceId = entry.getKey();
            Instant occurredAt = entry.getValue();
            if (deviceId == null || occurredAt == null) {
                continue;
            }
            try {
                OffsetDateTime time = OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
                int rows = deviceRepository.updateLastReportTimeIfNewer(deviceId, time);
                updated += rows;
                lastReportTimeBuffer.asMap().remove(deviceId, occurredAt);
            } catch (Exception e) {
                log.warn("DeviceLastReportTimeWriteBehindBuffer - flush failed: deviceId={}", deviceId, e);
            }
        }
        return updated;
    }
}

