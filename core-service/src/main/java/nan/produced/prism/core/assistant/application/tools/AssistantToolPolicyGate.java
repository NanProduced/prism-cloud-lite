package nan.produced.prism.core.assistant.application.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AssistantToolPolicyGate {

    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolPolicyCatalog policyCatalog;

    public ToolPolicyDecision evaluate(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return ToolPolicyDecision.denied("invalid_tool_name", "Tool name is required.");
        }
        String normalized = toolName.trim();
        if (toolRegistry.get(normalized) == null) {
            return ToolPolicyDecision.denied("unknown_tool", "Tool is not registered.");
        }
        AssistantToolPolicy policy = policyCatalog.policyFor(normalized);
        if (policy == null) {
            return ToolPolicyDecision.denied("missing_tool_policy", "Tool policy is not available.");
        }
        if (!policy.enabled()) {
            return ToolPolicyDecision.denied("tool_disabled", "Tool is disabled by policy.");
        }
        if (policy.requiresConfirmation()) {
            return ToolPolicyDecision.requiresConfirmation(policy);
        }
        return ToolPolicyDecision.allowed(policy);
    }

    public record ToolPolicyDecision(boolean allowed,
                                     boolean requiresConfirmation,
                                     String reasonCode,
                                     String reasonMessage,
                                     AssistantToolPolicy policy) {
        public static ToolPolicyDecision allowed(AssistantToolPolicy policy) {
            return new ToolPolicyDecision(true, false, null, null, policy);
        }

        public static ToolPolicyDecision requiresConfirmation(AssistantToolPolicy policy) {
            return new ToolPolicyDecision(false, true, "confirmation_required", "Tool requires confirmation.", policy);
        }

        public static ToolPolicyDecision denied(String code, String message) {
            return new ToolPolicyDecision(false, false, code, message, null);
        }
    }
}
