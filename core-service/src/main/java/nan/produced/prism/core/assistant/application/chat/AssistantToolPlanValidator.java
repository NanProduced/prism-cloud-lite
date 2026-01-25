package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.tools.AssistantToolRegistry;
import nan.produced.prism.core.assistant.application.tools.AssistantToolSchemaCatalog;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantToolPlanValidator {

    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolSchemaCatalog schemaCatalog;

    public ToolPlanValidationResult validate(AssistantToolPlan plan) {
        if (plan == null) {
            return ToolPlanValidationResult.error("empty_plan", "Tool plan is missing.");
        }

        List<ToolPlanValidationError> errors = new ArrayList<>();
        if (plan.toolCalls() != null) {
            for (AssistantToolPlan.PlannedToolCall call : plan.toolCalls()) {
                if (call == null || !StringUtils.hasText(call.name())) {
                    errors.add(new ToolPlanValidationError("invalid_tool_name", null, "Tool name is required."));
                    continue;
                }
                String toolName = call.name().trim();
                if (toolRegistry.get(toolName) == null) {
                    errors.add(new ToolPlanValidationError("unknown_tool", toolName, "Tool is not registered."));
                    continue;
                }
                if (!StringUtils.hasText(schemaCatalog.schemaFor(toolName))) {
                    errors.add(new ToolPlanValidationError("missing_schema", toolName, "Tool schema is not available."));
                }
                JsonNode arguments = call.arguments();
                if (arguments != null && !arguments.isObject()) {
                    errors.add(new ToolPlanValidationError("invalid_arguments", toolName, "Tool arguments must be an object."));
                }
            }
        }

        if (!errors.isEmpty()) {
            return new ToolPlanValidationResult(List.copyOf(errors));
        }
        return ToolPlanValidationResult.ok();
    }

    public record ToolPlanValidationResult(List<ToolPlanValidationError> errors) {

        public static ToolPlanValidationResult ok() {
            return new ToolPlanValidationResult(List.of());
        }

        public static ToolPlanValidationResult error(String code, String message) {
            return new ToolPlanValidationResult(List.of(new ToolPlanValidationError(code, null, message)));
        }

        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }

    public record ToolPlanValidationError(String code, String toolName, String message) {
    }
}
