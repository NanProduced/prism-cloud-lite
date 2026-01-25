package nan.produced.prism.core.assistant.application.tools;

import java.util.List;

public interface AssistantToolSchemaCatalog {

    record ToolSchema(String name, String description, String inputJsonSchema) {
    }

    String schemaFor(String toolName);

    List<ToolSchema> list();
}
