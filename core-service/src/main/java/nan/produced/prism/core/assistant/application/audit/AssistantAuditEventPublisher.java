package nan.produced.prism.core.assistant.application.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishToolCallEvent(AssistantToolCallAuditEvent event) {
        try {
            eventPublisher.publishEvent(event);
            log.debug("Published tool call audit event: toolCallId={}, toolName={}",
                    event.toolCallId(), event.toolName());
        } catch (Exception e) {
            log.warn("Failed to publish tool call audit event", e);
        }
    }

    public void publishTokenUsageEvent(AssistantChatTokenAuditEvent event) {
        try {
            eventPublisher.publishEvent(event);
            log.debug("Published token usage audit event: userId={}, totalTokens={}",
                    event.userId(), event.totalTokens());
        } catch (Exception e) {
            log.warn("Failed to publish token usage audit event", e);
        }
    }
}
