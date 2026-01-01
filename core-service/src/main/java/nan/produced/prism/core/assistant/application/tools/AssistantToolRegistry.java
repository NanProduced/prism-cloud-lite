package nan.produced.prism.core.assistant.application.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AssistantToolRegistry {

    private final Map<String, AssistantTool> tools;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        this.tools = tools == null ? Map.of() : tools.stream().collect(Collectors.toUnmodifiableMap(
                t -> t.name().trim(),
                Function.identity()
        ));
    }

    public AssistantTool get(String toolName) {
        if (toolName == null) {
            return null;
        }
        return tools.get(toolName);
    }

    public Set<String> names() {
        return tools.keySet();
    }
}
