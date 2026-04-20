package nan.produced.prism.core.assistant.application.audit;

import java.time.Instant;
import java.util.UUID;

public record AssistantChatTokenAuditEvent(
        UUID eventId,
        UUID userId,
        String traceId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String model,
        String provider,
        Instant timestamp
) {

    public static AssistantChatTokenAuditEvent create(
            UUID userId,
            String traceId,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String model,
            String provider
    ) {
        return new AssistantChatTokenAuditEvent(
                UUID.randomUUID(),
                userId,
                traceId,
                promptTokens,
                completionTokens,
                totalTokens,
                model,
                provider,
                Instant.now()
        );
    }
}
