package nan.produced.prism.core.assistant.application.tools;

public record AssistantToolPolicy(
        String name,
        AssistantToolRiskLevel riskLevel,
        boolean write,
        boolean enabled
) {
    public boolean requiresConfirmation() {
        return write;
    }
}
