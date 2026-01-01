package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;

public record AssistantToolCall(
        String toolCallId,
        String toolName,
        JsonNode input
) {
}

