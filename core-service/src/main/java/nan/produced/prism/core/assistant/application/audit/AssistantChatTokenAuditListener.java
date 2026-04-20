package nan.produced.prism.core.assistant.application.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.domain.AssistantChatTokenAuditEntity;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantChatTokenAuditRepositoryJpa;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatTokenAuditListener {

    private final AssistantChatTokenAuditRepositoryJpa tokenAuditRepository;

    @Async("backgroundTaskExecutor")
    @EventListener
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

            AssistantChatTokenAuditEntity entity = AssistantChatTokenAuditEntity.builder()
                    .id(event.eventId())
                    .userId(event.userId())
                    .traceId(traceId)
                    .promptTokens(event.promptTokens())
                    .completionTokens(event.completionTokens())
                    .totalTokens(event.totalTokens())
                    .model(event.model())
                    .provider(event.provider())
                    .createdAt(event.timestamp().atOffset(java.time.ZoneOffset.UTC))
                    .build();

            tokenAuditRepository.save(entity);

            log.debug("Token usage audit persisted: userId={}, totalTokens={}, model={}",
                    event.userId(), event.totalTokens(), event.model());

        } catch (Exception e) {
            log.warn("Failed to persist token usage audit: userId={}", event.userId(), e);
        }
    }
}
