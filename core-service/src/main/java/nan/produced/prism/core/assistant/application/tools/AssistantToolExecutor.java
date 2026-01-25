package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantToolAuditRepository;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolExecutor {

    private static final int AUDIT_SUMMARY_MAX_CHARS = 2_000;

    private final AssistantToolRegistry registry;
    private final AssistantToolAuditRepository auditRepository;
    private final AssistantToolPolicyGate policyGate;
    private final ObjectMapper objectMapper;

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
                String inputSummary = summarizeJson(call.input());
                String outputSummary = summarizeJson(output);
                long auditElapsed = elapsedMs > 0 ? elapsedMs : (System.currentTimeMillis() - startedAt);
                auditRepository.insert(
                        UUID.randomUUID(),
                        userId,
                        call.toolCallId(),
                        call.toolName(),
                        call.input(),
                        traceId,
                        inputSummary,
                        outputSummary,
                        success,
                        auditElapsed,
                        errorText
                );
            } catch (Exception e) {
                log.debug("assistant tool audit insert failed", e);
            }
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
