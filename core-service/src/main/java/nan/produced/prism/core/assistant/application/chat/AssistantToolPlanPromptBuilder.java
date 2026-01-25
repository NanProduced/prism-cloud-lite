package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.tools.AssistantToolSchemaCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantToolPlanPromptBuilder {

    private final AssistantToolSchemaCatalog toolSchemaCatalog;

    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("You are a tool planner.\n");
        sb.append("Return STRICT JSON only. No prose, no markdown.\n");
        sb.append("Output format:\n");
        sb.append("{\"toolCalls\":[{\"name\":\"...\",\"arguments\":{...}}]}\n");
        sb.append("If no tool is needed, return {\"no_tool\":true}.\n");
        sb.append("Only use the tools listed below.\n\n");
        sb.append("Tools:\n");
        for (AssistantToolSchemaCatalog.ToolSchema spec : orderedSchemas()) {
            sb.append("- name: ").append(spec.name()).append("\n");
            sb.append("  description: ").append(spec.description()).append("\n");
            sb.append("  arguments_schema: ").append(spec.inputJsonSchema()).append("\n");
        }
        return sb.toString();
    }

    private List<AssistantToolSchemaCatalog.ToolSchema> orderedSchemas() {
        List<AssistantToolSchemaCatalog.ToolSchema> schemas = toolSchemaCatalog.list();
        return schemas == null ? List.of() : schemas;
    }
}
