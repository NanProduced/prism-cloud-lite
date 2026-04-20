package nan.produced.prism.core.assistant.application.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AssistantToolCallAuditEvent(
        UUID eventId,
        UUID userId,
        String toolCallId,
        String toolName,
        JsonNode input,
        JsonNode output,
        boolean success,
        long elapsedMs,
        String errorMessage,
        String traceId,
        Instant timestamp
) {

    public static AssistantToolCallAuditEvent create(
            UUID userId,
            String toolCallId,
            String toolName,
            JsonNode input,
            JsonNode output,
            boolean success,
            long elapsedMs,
            String errorMessage,
            String traceId
    ) {
        return new AssistantToolCallAuditEvent(
                UUID.randomUUID(),
                userId,
                toolCallId,
                toolName,
                input,
                output,
                success,
                elapsedMs,
                errorMessage,
                traceId,
                Instant.now()
        );
    }
}
