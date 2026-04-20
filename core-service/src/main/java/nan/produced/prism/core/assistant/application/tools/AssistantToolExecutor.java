package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.application.audit.AssistantAuditEventPublisher;
import nan.produced.prism.core.assistant.application.audit.AssistantToolCallAuditEvent;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolExecutor {

    private final AssistantToolRegistry registry;
    private final AssistantToolPolicyGate policyGate;
    private final AssistantAuditEventPublisher auditEventPublisher;

    public record ToolExecutionResult(boolean success, JsonNode output, String errorText, long elapsedMs) {
    }

    public ToolExecutionResult execute(UUID userId, AssistantToolCall call) {
        long startedAt = System.currentTimeMillis();
        boolean success = false;
        JsonNode output = null;
        String errorText = null;
        long elapsedMs = 0L;

        try {
            AssistantToolPolicyGate.ToolPolicyDecision decision = policyGate.evaluate(call.toolName());
            if (!decision.allowed()) {
                errorText = decision.reasonMessage();
                elapsedMs = System.currentTimeMillis() - startedAt;
                return new ToolExecutionResult(false, null, errorText, elapsedMs);
            }
            AssistantTool tool = registry.get(call.toolName());
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool: " + call.toolName());
            }
            output = tool.execute(userId, call.input());
            success = true;
            elapsedMs = System.currentTimeMillis() - startedAt;
            return new ToolExecutionResult(true, output, null, elapsedMs);
        } catch (Exception e) {
            errorText = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("assistant tool failed: toolName={}, toolCallId={}", call.toolName(), call.toolCallId(), e);
            elapsedMs = System.currentTimeMillis() - startedAt;
            return new ToolExecutionResult(false, null, errorText, elapsedMs);
        } finally {
            try {
                String traceId = TraceUtils.getTraceId();
                if ("unknown".equalsIgnoreCase(traceId)) {
                    traceId = null;
                }
                long auditElapsed = elapsedMs > 0 ? elapsedMs : (System.currentTimeMillis() - startedAt);

                AssistantToolCallAuditEvent event = AssistantToolCallAuditEvent.create(
                        userId,
                        call.toolCallId(),
                        call.toolName(),
                        call.input(),
                        output,
                        success,
                        auditElapsed,
                        errorText,
                        traceId
                );

                auditEventPublisher.publishToolCallEvent(event);

            } catch (Exception e) {
                log.debug("assistant tool audit event publish failed", e);
            }
        }
    }
}
