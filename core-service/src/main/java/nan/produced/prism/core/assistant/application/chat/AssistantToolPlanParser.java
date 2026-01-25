package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantToolPlanParser {

    private final ObjectMapper objectMapper;

    public ToolPlanParseResult parse(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return ToolPlanParseResult.error("empty_output", "Tool plan output is empty.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            return ToolPlanParseResult.error("invalid_json", "Tool plan is not valid JSON.");
        }

        if (root == null || !root.isObject()) {
            return ToolPlanParseResult.error("invalid_payload", "Tool plan must be a JSON object.");
        }

        boolean noTool = root.has("no_tool") && root.get("no_tool").asBoolean(false);
        JsonNode toolCallsNode = root.get("toolCalls");
        if (toolCallsNode == null) {
            toolCallsNode = root.get("tool_calls");
        }

        List<AssistantToolPlan.PlannedToolCall> toolCalls = new ArrayList<>();
        if (toolCallsNode != null && !toolCallsNode.isNull()) {
            if (!toolCallsNode.isArray()) {
                return ToolPlanParseResult.error("invalid_tool_calls", "toolCalls must be an array.");
            }
            for (JsonNode callNode : toolCallsNode) {
                if (callNode == null || !callNode.isObject()) {
                    return ToolPlanParseResult.error("invalid_tool_call", "Each tool call must be an object.");
                }
                JsonNode nameNode = callNode.get("name");
                if (nameNode == null || !nameNode.isTextual() || !StringUtils.hasText(nameNode.asText())) {
                    return ToolPlanParseResult.error("invalid_tool_name", "toolCalls[].name must be a non-empty string.");
                }
                String name = nameNode.asText().trim();
                JsonNode argumentsNode = callNode.get("arguments");
                argumentsNode = normalizeArguments(argumentsNode);
                if (argumentsNode == null) {
                    return ToolPlanParseResult.error("invalid_tool_arguments", "toolCalls[].arguments must be an object.");
                }
                toolCalls.add(new AssistantToolPlan.PlannedToolCall(name, argumentsNode));
            }
        }

        if (toolCalls.isEmpty() && !noTool) {
            return ToolPlanParseResult.error("missing_tool_calls", "toolCalls is required unless no_tool is true.");
        }

        return ToolPlanParseResult.success(new AssistantToolPlan(List.copyOf(toolCalls), noTool));
    }

    private JsonNode normalizeArguments(JsonNode argumentsNode) {
        if (argumentsNode == null || argumentsNode.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (argumentsNode.isObject()) {
            return argumentsNode;
        }
        if (argumentsNode.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(argumentsNode.asText());
                return parsed != null && parsed.isObject() ? parsed : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public record ToolPlanParseResult(AssistantToolPlan plan, ToolPlanParseError error) {

        public static ToolPlanParseResult success(AssistantToolPlan plan) {
            return new ToolPlanParseResult(plan, null);
        }

        public static ToolPlanParseResult error(String code, String message) {
            return new ToolPlanParseResult(null, new ToolPlanParseError(code, message));
        }

        public boolean success() {
            return error == null;
        }
    }

    public record ToolPlanParseError(String code, String message) {
    }
}
