package nan.produced.prism.core.assistant.application.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantChatTokenAuditRepository;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatTokenAuditListener {

    private final AssistantChatTokenAuditRepository tokenAuditRepository;

    @Async("backgroundTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onTokenAuditEvent(AssistantChatTokenAuditEvent event) {
        if (event == null) {
            return;
        }

        try {
            String traceId = event.traceId();
            if (traceId == null || "unknown".equalsIgnoreCase(traceId)) {
                traceId = TraceUtils.getTraceId();
                if ("unknown".equalsIgnoreCase(traceId)) {
                    traceId = null;
                }
            }

            tokenAuditRepository.insert(
                    event.eventId(),
                    event.userId(),
                    traceId,
                    event.promptTokens(),
                    event.completionTokens(),
                    event.totalTokens(),
                    event.model(),
                    event.provider(),
                    null,
                    null
            );

            log.debug("Token usage audit persisted: userId={}, totalTokens={}, model={}",
                    event.userId(), event.totalTokens(), event.model());

        } catch (Exception e) {
            log.warn("Failed to persist token usage audit: userId={}", event.userId(), e);
        }
    }
}
