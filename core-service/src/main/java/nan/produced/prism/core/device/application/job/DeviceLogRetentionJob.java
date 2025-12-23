package nan.produced.prism.core.device.application.job;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceLogRepositoryJpa;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLogRetentionJob {

    private static final int RETENTION_DAYS = 60;

    private final DeviceLogRepositoryJpa deviceLogRepositoryJpa;

    /**
     * 保留最近 60 天日志：每日 UTC 03:30 清理一次。
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredDeviceLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        int deleted = deviceLogRepositoryJpa.deleteExpired(cutoff);
        if (deleted > 0) {
            log.info("DeviceLogRetentionJob - deleted {} device logs before {}", deleted, cutoff);
        }
    }
}

