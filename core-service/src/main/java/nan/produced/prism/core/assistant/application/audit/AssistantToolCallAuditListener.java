package nan.produced.prism.core.assistant.application.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.domain.AssistantToolCallAuditEntity;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantToolCallAuditRepositoryJpa;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantToolCallAuditListener {

    private static final int AUDIT_SUMMARY_MAX_CHARS = 2_000;

    private final AssistantToolCallAuditRepositoryJpa auditRepository;
    private final AuditSensitiveDataMasker sensitiveDataMasker;
    private final ObjectMapper objectMapper;

    @Async("backgroundTaskExecutor")
    @EventListener
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
            String inputJsonString = (maskedInput != null) ? maskedInput.toString() : null;

            AssistantToolCallAuditEntity entity = AssistantToolCallAuditEntity.builder()
                    .id(event.eventId())
                    .userId(event.userId())
                    .toolCallId(event.toolCallId())
                    .toolName(event.toolName())
                    .inputJson(inputJsonString)
                    .traceId(traceId)
                    .inputSummary(inputSummary)
                    .outputSummary(outputSummary)
                    .success(event.success())
                    .elapsedMs(event.elapsedMs())
                    .errorMessage(event.errorMessage())
                    .createdAt(event.timestamp().atOffset(ZoneOffset.UTC))
                    .build();

            auditRepository.save(entity);

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
