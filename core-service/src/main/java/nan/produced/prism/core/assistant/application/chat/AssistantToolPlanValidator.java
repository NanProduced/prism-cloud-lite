package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nan.produced.prism.core.assistant.application.tools.AssistantToolRegistry;
import nan.produced.prism.core.assistant.application.tools.AssistantToolSchemaCatalog;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantToolPlanValidator {

    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolSchemaCatalog schemaCatalog;
    private final ObjectMapper objectMapper;

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
                } else {
                    errors.addAll(validateArgumentsAgainstSchema(toolName, arguments));
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

    private List<ToolPlanValidationError> validateArgumentsAgainstSchema(String toolName, JsonNode arguments) {
        String schemaJson = schemaCatalog.schemaFor(toolName);
        if (!StringUtils.hasText(schemaJson)) {
            return List.of();
        }

        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (Exception e) {
            return List.of(new ToolPlanValidationError("invalid_schema", toolName, "Tool schema is invalid JSON."));
        }

        if (schema == null || !schema.isObject()) {
            return List.of(new ToolPlanValidationError("invalid_schema", toolName, "Tool schema must be a JSON object."));
        }

        List<ToolPlanValidationError> errors = new ArrayList<>();

        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode req : required) {
                if (req == null || !req.isTextual()) {
                    continue;
                }
                String field = req.asText();
                if (!StringUtils.hasText(field)) {
                    continue;
                }
                if (arguments == null || !arguments.hasNonNull(field)) {
                    errors.add(new ToolPlanValidationError("missing_required_argument", toolName,
                            "Missing required argument: " + field));
                }
            }
        }

        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject() && arguments != null && arguments.isObject()) {
            Iterator<String> fields = arguments.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                JsonNode propSchema = properties.get(field);
                if (propSchema == null || !propSchema.isObject()) {
                    continue;
                }
                JsonNode typeNode = propSchema.get("type");
                if (typeNode == null || !typeNode.isTextual()) {
                    continue;
                }
                String expectedType = typeNode.asText();
                JsonNode value = arguments.get(field);
                if (value == null || value.isNull()) {
                    continue;
                }
                if (!isTypeCompatible(expectedType, value)) {
                    errors.add(new ToolPlanValidationError("invalid_argument_type", toolName,
                            "Argument '" + field + "' must be " + expectedType + "."));
                }
            }
        }

        return errors;
    }

    private static boolean isTypeCompatible(String expectedType, JsonNode value) {
        if (value == null) {
            return true;
        }
        return switch (expectedType) {
            case "integer" -> value.isNumber();
            case "number" -> value.isNumber();
            case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> true;
        };
    }
}
