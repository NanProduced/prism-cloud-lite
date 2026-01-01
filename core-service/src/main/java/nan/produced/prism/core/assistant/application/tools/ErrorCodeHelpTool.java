package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.chat.AssistantRagContextService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ErrorCodeHelpTool implements AssistantTool {

    private static final int MAX_CONTEXT_CHARS = 4000;

    private final ObjectMapper objectMapper;
    private final AssistantRagContextService ragContextService;

    @Override
    public String name() {
        return "getErrorCodeHelp";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        String errorCode = textOrNull(input, "errorCode");
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }

        AssistantRagContextService.RagContext rag = ragContextService.buildContext("error code " + errorCode.trim());
        String context = rag.contextText() == null ? "" : rag.contextText();
        if (context.length() > MAX_CONTEXT_CHARS) {
            context = context.substring(0, MAX_CONTEXT_CHARS);
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("errorCode", errorCode.trim());
        out.put("context", context);
        out.set("sources", objectMapper.valueToTree(rag.sources()));
        return out;
    }

    private static String textOrNull(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode v = input.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }
}

