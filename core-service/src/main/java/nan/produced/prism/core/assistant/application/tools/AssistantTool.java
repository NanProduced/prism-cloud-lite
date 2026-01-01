package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface AssistantTool {

    String name();

    JsonNode execute(UUID userId, JsonNode input);
}

