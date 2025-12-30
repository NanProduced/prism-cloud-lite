package nan.produced.prism.core.resource.application.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.resource.application.service.ResourceTombstoneService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceTombstoneRetentionJob {

    private final ResourceTombstoneService resourceTombstoneService;

    /**
     * 清理过期 tombstone（默认：保留 60 天；每日 UTC 03:45 清理一次）。
     */
    @Scheduled(cron = "${prism.resource-tombstone.cleanup-cron:0 45 3 * * *}",
        zone = "${prism.resource-tombstone.cleanup-zone:UTC}")
    @Transactional
    public void cleanupExpired() {
        int deleted = resourceTombstoneService.cleanupExpired();
        if (deleted > 0) {
            log.info("ResourceTombstoneRetentionJob - deleted {} tombstones", deleted);
        }
    }
}

