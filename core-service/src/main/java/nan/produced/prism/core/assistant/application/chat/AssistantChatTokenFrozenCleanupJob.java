package nan.produced.prism.core.assistant.application.chat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatTokenFrozenCleanupJob {

    private final AssistantChatTokenBudgetService tokenBudgetService;

    private static final Duration DEFAULT_MAX_FROZEN_AGE = Duration.ofMinutes(30);

    @Scheduled(cron = "${assistant.chat.token-budget.frozen-cleanup-cron:0 */5 * * * *}",
        zone = "${assistant.chat.token-budget.frozen-cleanup-zone:UTC}")
    @Transactional
    public void cleanupExpiredFrozenTokens() {
        int cleaned = tokenBudgetService.cleanupExpiredFrozenTokens(DEFAULT_MAX_FROZEN_AGE);
        if (cleaned > 0) {
            log.warn("AssistantChatTokenFrozenCleanupJob - released {} expired frozen token records (age > {})",
                    cleaned, DEFAULT_MAX_FROZEN_AGE);
        }
    }
}
