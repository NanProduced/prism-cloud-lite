package nan.produced.prism.core.assistant.application.chat;

import org.springframework.stereotype.Component;

@Component
public class AssistantToolPlanGate {

    public ToolPlanGateResult evaluate(AssistantToolPlan plan, AssistantChatTierLimits limits) {
        if (plan == null) {
            return ToolPlanGateResult.deny("Tool plan is missing.");
        }
        int maxCalls = limits != null ? limits.toolMaxCallsPerRound() : 0;
        int callCount = plan.toolCalls() != null ? plan.toolCalls().size() : 0;
        if (maxCalls > 0 && callCount > maxCalls) {
            return ToolPlanGateResult.deny("Tool calls exceed per-round limit.");
        }
        return ToolPlanGateResult.allow();
    }

    public record ToolPlanGateResult(boolean allowed, String reason) {
        public static ToolPlanGateResult allow() {
            return new ToolPlanGateResult(true, null);
        }

        public static ToolPlanGateResult deny(String reason) {
            return new ToolPlanGateResult(false, reason);
        }
    }
}
