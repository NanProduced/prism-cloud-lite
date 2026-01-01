package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantToolAuditRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolExecutor {

    private final AssistantToolRegistry registry;
    private final AssistantToolAuditRepository auditRepository;

    public record ToolExecutionResult(boolean success, JsonNode output, String errorText, long elapsedMs) {
    }

    public ToolExecutionResult execute(UUID userId, AssistantToolCall call) {
        long startedAt = System.currentTimeMillis();
        boolean success = false;
        JsonNode output = null;
        String errorText = null;

        try {
            AssistantTool tool = registry.get(call.toolName());
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool: " + call.toolName());
            }
            output = tool.execute(userId, call.input());
            success = true;
            return new ToolExecutionResult(true, output, null, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            errorText = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("assistant tool failed: toolName={}, toolCallId={}", call.toolName(), call.toolCallId(), e);
            return new ToolExecutionResult(false, null, errorText, System.currentTimeMillis() - startedAt);
        } finally {
            try {
                auditRepository.insert(
                        UUID.randomUUID(),
                        userId,
                        call.toolCallId(),
                        call.toolName(),
                        call.input(),
                        success,
                        System.currentTimeMillis() - startedAt,
                        errorText
                );
            } catch (Exception e) {
                log.debug("assistant tool audit insert failed", e);
            }
        }
    }
}

