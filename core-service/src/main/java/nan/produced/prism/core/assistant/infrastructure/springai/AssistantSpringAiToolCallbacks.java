package nan.produced.prism.core.assistant.infrastructure.springai;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.tools.AssistantToolRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AssistantSpringAiToolCallbacks {

    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolSpecs toolSpecs;

    public List<ToolCallback> buildAll() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : toolRegistry.names()) {
            AssistantToolSpecs.ToolSpec spec = toolSpecs.get(name);
            if (spec == null) {
                continue;
            }
            callbacks.add(new SchemaOnlyToolCallback(spec));
        }
        return List.copyOf(callbacks);
    }

    /**
     * MVP: used for exposing tool schema to the model. Execution is handled manually in the tool loop.
     */
    private static class SchemaOnlyToolCallback implements ToolCallback {

        private final ToolDefinition definition;

        private SchemaOnlyToolCallback(AssistantToolSpecs.ToolSpec spec) {
            this.definition = ToolDefinition.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .inputSchema(spec.inputJsonSchema())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String input) {
            throw new UnsupportedOperationException("Tool execution is handled by server-side tool loop.");
        }
    }
}
