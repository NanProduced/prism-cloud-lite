package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ToolCallService {

    private final AssistantChatProperties properties;
    private final AssistantToolPlanner toolPlanner;
    private final AssistantToolExecutor toolExecutor;
    private final AssistantNavigationToolResolver navigationToolResolver;
    private final AssistantCommandLogPickerResolver commandLogPickerResolver;
    private final AssistantDevicePickerResolver devicePickerResolver;
    private final AssistantPendingToolCallStore pendingToolCallStore;
    private final ObjectMapper objectMapper;

    public record ToolCallOutcome(Object toolResultForPrompt, boolean finished) {
    }

    public ToolCallOutcome handle(UUID userId,
                                  String userText,
                                  AssistantSelectionTokenParser.ParseResult selection,
                                  AssistantChatTierLimits limits,
                                  AiUiMessageSseWriter writer) {
        Object toolResultForPrompt = null;

        if (selection.hasAnySelection()) {
            AssistantToolCall injectedToolCall = selection.toInjectedToolCall(objectMapper);
            if (injectedToolCall != null) {
                writer.toolInputAvailable(injectedToolCall.toolCallId(), injectedToolCall.toolName(), injectedToolCall.input(), true);
                var exec = toolExecutor.execute(userId, injectedToolCall);
                if (exec.success()) {
                    toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                    writer.toolOutputAvailable(injectedToolCall.toolCallId(), exec.output(), true);
                } else {
                    writer.toolOutputError(injectedToolCall.toolCallId(), exec.errorText(), true);
                }
            }
        }

        AssistantNavigationToolResolver.NavigationTarget nav = navigationToolResolver.resolve(userText);
        if (nav != null) {
            String toolCallId = "tool-" + UUID.randomUUID();
            writer.toolInputAvailable(toolCallId, "navigateToPage", Map.of("path", nav.path(), "label", nav.label()));
            writer.finish("stop", null);
            return new ToolCallOutcome(toolResultForPrompt, true);
        }

        if (!selection.hasAnySelection()) {
            AssistantCommandLogPickerResolver.PickPayload commandPick = commandLogPickerResolver.resolve(userId, userText);
            if (commandPick != null) {
                String toolCallId = "tool-" + UUID.randomUUID();
                pendingToolCallStore.set(userId, toolCallId, "pickCommandLog");
                writer.toolInputAvailable(toolCallId, "pickCommandLog", commandPick);
                writer.finish("tool-calls", null);
                return new ToolCallOutcome(toolResultForPrompt, true);
            }

            AssistantDevicePickerResolver.PickPayload devicePick = devicePickerResolver.resolve(userId, userText);
            if (devicePick != null) {
                String toolCallId = "tool-" + UUID.randomUUID();
                pendingToolCallStore.set(userId, toolCallId, "pickDevice");
                writer.toolInputAvailable(toolCallId, "pickDevice", devicePick);
                writer.finish("tool-calls", null);
                return new ToolCallOutcome(toolResultForPrompt, true);
            }
        }

        AssistantToolCall plannedToolCall = "spring-ai".equalsIgnoreCase(properties.engine())
                ? null
                : toolPlanner.plan(userText);
        if (toolResultForPrompt == null && !"spring-ai".equalsIgnoreCase(properties.engine()) && plannedToolCall != null) {
            writer.toolInputAvailable(plannedToolCall.toolCallId(), plannedToolCall.toolName(), plannedToolCall.input(), true);
            var exec = toolExecutor.execute(userId, plannedToolCall);
            if (exec.success()) {
                toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                writer.toolOutputAvailable(plannedToolCall.toolCallId(), exec.output(), true);
            } else {
                writer.toolOutputError(plannedToolCall.toolCallId(), exec.errorText(), true);
            }
        }

        return new ToolCallOutcome(toolResultForPrompt, false);
    }

    private Object truncateJson(JsonNode node, int maxChars) {
        if (node == null || maxChars <= 0) {
            return node;
        }
        try {
            String json = objectMapper.writeValueAsString(node);
            if (json.length() <= maxChars) {
                return node;
            }
            return Map.of("_truncated", true, "_maxChars", maxChars, "_text", json.substring(0, maxChars));
        } catch (Exception e) {
            return node;
        }
    }
}
