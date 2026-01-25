package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolCallService {

    private final AssistantToolExecutor toolExecutor;
    private final AssistantToolPlanService toolPlanService;
    private final AssistantNavigationToolResolver navigationToolResolver;
    private final AssistantCommandLogPickerResolver commandLogPickerResolver;
    private final AssistantDevicePickerResolver devicePickerResolver;
    private final AssistantPendingToolCallStore pendingToolCallStore;
    private final ObjectMapper objectMapper;

    public record ToolCallOutcome(Object toolResultForPrompt, boolean finished) {
    }

    public ToolCallOutcome handle(UUID userId,
                                  String userText,
                                  List<AiSdkChatRequestParser.ChatMessage> conversation,
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

        if (toolResultForPrompt != null) {
            return new ToolCallOutcome(toolResultForPrompt, false);
        }

        AssistantToolPlanService.ToolPlanDecision decision =
                toolPlanService.plan(userId, limits, conversation, userText);
        if (decision != null && decision.success()) {
            toolResultForPrompt = executePlannedTools(userId, decision.plan(), limits, writer);
        } else if (decision != null) {
            log.info("tool plan skipped: userId={}, code={}, message={}", userId, decision.errorCode(), decision.errorMessage());
        } else {
            log.info("tool plan skipped: userId={}, reason=decision_missing", userId);
        }

        return new ToolCallOutcome(toolResultForPrompt, false);
    }

    private Object executePlannedTools(UUID userId,
                                       AssistantToolPlan plan,
                                       AssistantChatTierLimits limits,
                                       AiUiMessageSseWriter writer) {
        if (plan == null || !plan.hasToolCalls()) {
            return null;
        }
        int maxCalls = limits != null ? limits.toolMaxCallsPerRound() : 0;
        int maxChars = limits != null ? limits.toolResultMaxChars() : 0;
        var results = objectMapper.createArrayNode();
        int count = 0;
        for (AssistantToolPlan.PlannedToolCall call : plan.toolCalls()) {
            if (call == null || !StringUtils.hasText(call.name())) {
                continue;
            }
            count++;
            if (maxCalls > 0 && count > maxCalls) {
                results.add(buildErrorResult("tool-limit", call.name(), "Too many tool calls in one request."));
                break;
            }
            String toolCallId = "tool-" + UUID.randomUUID();
            writer.toolInputAvailable(toolCallId, call.name(), call.arguments(), true);
            var exec = toolExecutor.execute(userId, new AssistantToolCall(toolCallId, call.name(), call.arguments()));
            if (exec.success()) {
                writer.toolOutputAvailable(toolCallId, exec.output(), true);
                results.add(buildSuccessResult(toolCallId, call.name(), call.arguments(), exec.output(), maxChars));
            } else {
                writer.toolOutputError(toolCallId, exec.errorText(), true);
                results.add(buildErrorResult(toolCallId, call.name(), exec.errorText()));
            }
        }
        return results.isEmpty() ? null : results;
    }

    private JsonNode buildSuccessResult(String toolCallId,
                                        String toolName,
                                        JsonNode input,
                                        JsonNode output,
                                        int maxChars) {
        var item = objectMapper.createObjectNode();
        item.put("toolCallId", toolCallId);
        item.put("toolName", toolName);
        if (input != null) {
            item.set("input", input);
        }
        Object truncated = truncateJson(output, maxChars);
        item.set("output", objectMapper.valueToTree(truncated));
        return item;
    }

    private JsonNode buildErrorResult(String toolCallId, String toolName, String errorText) {
        var item = objectMapper.createObjectNode();
        item.put("toolCallId", toolCallId);
        item.put("toolName", toolName);
        var error = objectMapper.createObjectNode();
        error.put("error", StringUtils.hasText(errorText) ? errorText : "Tool error");
        item.set("error", error);
        return item;
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
