package nan.produced.prism.core.assistant.application.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantToolAuditRepository;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantToolCallAuditListener {

    private static final int AUDIT_SUMMARY_MAX_CHARS = 2_000;

    private final AssistantToolAuditRepository auditRepository;
    private final AuditSensitiveDataMasker sensitiveDataMasker;
    private final ObjectMapper objectMapper;

    @Async("backgroundTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onToolCallAuditEvent(AssistantToolCallAuditEvent event) {
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

            JsonNode maskedInput = sensitiveDataMasker.mask(event.input());
            JsonNode maskedOutput = sensitiveDataMasker.mask(event.output());

            String inputSummary = summarizeJson(maskedInput);
            String outputSummary = summarizeJson(maskedOutput);

            auditRepository.insert(
                    UUID.randomUUID(),
                    event.userId(),
                    event.toolCallId(),
                    event.toolName(),
                    maskedInput,
                    traceId,
                    inputSummary,
                    outputSummary,
                    event.success(),
                    event.elapsedMs(),
                    event.errorMessage()
            );

            log.debug("Tool call audit persisted: toolCallId={}, toolName={}, success={}",
                    event.toolCallId(), event.toolName(), event.success());

        } catch (Exception e) {
            log.warn("Failed to persist tool call audit: toolCallId={}, toolName={}",
                    event.toolCallId(), event.toolName(), e);
        }
    }

    private String summarizeJson(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(node);
            if (json.length() <= AUDIT_SUMMARY_MAX_CHARS) {
                return json;
            }
            return json.substring(0, AUDIT_SUMMARY_MAX_CHARS) + "...(truncated)";
        } catch (Exception e) {
            return null;
        }
    }
}
