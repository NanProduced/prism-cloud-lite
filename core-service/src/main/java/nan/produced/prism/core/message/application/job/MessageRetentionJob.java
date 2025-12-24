package nan.produced.prism.core.message.application.job;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.message.infrastructure.config.MessageCenterProperties;
import nan.produced.prism.core.message.infrastructure.persistence.MessageRepositoryJpa;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetentionJob {

    private final MessageRepositoryJpa messageRepositoryJpa;
    private final MessageCenterProperties messageCenterProperties;

    /**
     * 清理过期消息（默认：保留最近 60 天消息；每日 UTC 03:40 清理一次）。
     */
    @Scheduled(cron = "#{@messageCenterProperties.retention.cleanupCron}",
        zone = "#{@messageCenterProperties.retention.cleanupZone}")
    @Transactional
    public void cleanupExpiredMessages() {
        int days = Math.max(1, messageCenterProperties.getRetention().getDays());
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        int deleted = messageRepositoryJpa.deleteExpired(cutoff);
        if (deleted > 0) {
            log.info("MessageRetentionJob - deleted {} messages before {}", deleted, cutoff);
        }
    }
}
