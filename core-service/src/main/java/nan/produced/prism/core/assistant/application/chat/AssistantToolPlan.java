package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record AssistantToolPlan(List<PlannedToolCall> toolCalls, boolean noTool) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record PlannedToolCall(String name, JsonNode arguments) {
    }
}
