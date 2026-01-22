package nan.produced.prism.core.assistant.application.chat;

final class AssistantChatFinishReasonNormalizer {

    private AssistantChatFinishReasonNormalizer() {
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "stop";
        }
        String value = raw.trim();
        return switch (value) {
            case "stop", "length", "content-filter", "tool-calls", "error", "other" -> value;
            case "tool_calls" -> "tool-calls";
            default -> "other";
        };
    }
}
