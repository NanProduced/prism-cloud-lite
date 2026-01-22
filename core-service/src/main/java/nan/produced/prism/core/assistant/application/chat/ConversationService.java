package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final AssistantSelectionTokenParser selectionTokenParser;
    private final AssistantPendingToolCallStore pendingToolCallStore;

    public record ConversationResolution(
            List<AiSdkChatRequestParser.ChatMessage> conversation,
            AssistantSelectionTokenParser.ParseResult selection,
            String userText,
            String errorText
    ) {
        public boolean hasError() {
            return StringUtils.hasText(errorText);
        }
    }

    public ConversationResolution resolve(UUID userId, AssistantChatTierLimits limits, JsonNode request) {
        List<AiSdkChatRequestParser.ChatMessage> conversation = trimConversation(
                AiSdkChatRequestParser.parseUserAndAssistantMessages(request),
                limits.historyMaxMessages(),
                limits.historyMaxChars()
        );
        String rawUserText = lastUserText(conversation);
        AssistantSelectionTokenParser.ParseResult selection = selectionTokenParser.parse(rawUserText);

        conversation = sanitizeUserMessages(conversation);
        String userText = lastUserText(conversation);
        userText = StringUtils.hasText(userText) ? userText : null;

        String lastRole = AiSdkChatRequestParser.lastNonSystemRole(request);
        AssistantPendingToolCallStore.PendingToolCall pending = pendingToolCallStore.get(userId);
        ToolSelection selectionFromTool = null;

        if (pending != null && pending.toolCallId() != null) {
            AiSdkChatRequestParser.ToolOutput toolOutput =
                    AiSdkChatRequestParser.findToolOutput(request, pending.toolCallId(), pending.toolName());
            if (toolOutput != null) {
                if ("output-available".equalsIgnoreCase(toolOutput.state()) && toolOutput.output() != null) {
                    selectionFromTool = parseToolSelectionNode(toolOutput.output());
                    if (selectionFromTool == null) {
                        return new ConversationResolution(conversation, selection, userText, "非法操作或选择已失效");
                    }
                    pendingToolCallStore.clear(userId);
                } else if ("output-error".equalsIgnoreCase(toolOutput.state())) {
                    pendingToolCallStore.clear(userId);
                    String errorText = toolOutput.errorText() != null ? toolOutput.errorText() : "工具执行失败或用户取消";
                    return new ConversationResolution(conversation, selection, userText, errorText);
                }
            } else if ("user".equals(lastRole)) {
                pendingToolCallStore.clear(userId);
            }
        } else if (pending != null && "user".equals(lastRole)) {
            pendingToolCallStore.clear(userId);
        }

        if (selectionFromTool != null && selectionFromTool.hasAnySelection()) {
            selection = selectionFromTool.toTokenLikeSelection(selection.cleanedText());
        }

        if (!StringUtils.hasText(userText)) {
            userText = StringUtils.hasText(selection.cleanedText()) ? selection.cleanedText() : rawUserText;
        }
        if (StringUtils.hasText(selection.cleanedText())) {
            userText = selection.cleanedText();
        }
        if (!StringUtils.hasText(userText)) {
            return new ConversationResolution(conversation, selection, userText, "Missing user message");
        }

        return new ConversationResolution(conversation, selection, userText, null);
    }

    /**
     * 会对所有 user 消息调用 stripKnownTokens，把这些 token 从 user 文本中剥离掉
     */
    private List<AiSdkChatRequestParser.ChatMessage> sanitizeUserMessages(List<AiSdkChatRequestParser.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AiSdkChatRequestParser.ChatMessage> out = new ArrayList<>(messages.size());
        for (AiSdkChatRequestParser.ChatMessage m : messages) {
            if (m != null && "user".equals(m.role()) && StringUtils.hasText(m.content())) {
                String cleaned = selectionTokenParser.stripKnownTokens(m.content());
                out.add(new AiSdkChatRequestParser.ChatMessage(m.role(), cleaned));
            } else {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }

    /**
     * 只保留最后一段到“最后一条 user 消息”为止，并从尾部往前累计字符数，超出上限就丢更早的消息。
     */
    private static List<AiSdkChatRequestParser.ChatMessage> trimConversation(List<AiSdkChatRequestParser.ChatMessage> messages,
                                                                             int maxMessages,
                                                                             int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int mm = maxMessages <= 0 ? Integer.MAX_VALUE : maxMessages;
        int mc = maxChars <= 0 ? Integer.MAX_VALUE : maxChars;

        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx < 0) {
            return List.of();
        }

        int start = Math.max(0, (lastUserIdx + 1) - mm);
        List<AiSdkChatRequestParser.ChatMessage> tail = messages.subList(start, lastUserIdx + 1);

        int total = 0;
        int keepFrom = tail.size() - 1;
        for (int i = tail.size() - 1; i >= 0; i--) {
            AiSdkChatRequestParser.ChatMessage m = tail.get(i);
            int len = m != null && m.content() != null ? m.content().length() : 0;
            if (i != tail.size() - 1 && total + len > mc) {
                keepFrom = i + 1;
                break;
            }
            total += len;
            keepFrom = i;
        }
        return List.copyOf(tail.subList(keepFrom, tail.size()));
    }

    /**
     * 获取最后一条用户消息的文本
     */
    private static String lastUserText(List<AiSdkChatRequestParser.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiSdkChatRequestParser.ChatMessage m = messages.get(i);
            if (m != null && "user".equals(m.role()) && StringUtils.hasText(m.content())) {
                return m.content().trim();
            }
        }
        return null;
    }

    /**
     * 解析工具选择
     */
    private ToolSelection parseToolSelectionNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        boolean fleet = node.has("fleet") && node.get("fleet").asBoolean(false);
        Long deviceId = parseLongNode(node.get("deviceId"));
        Long commandLogId = parseLongNode(node.get("commandLogId"));
        ToolSelection selection = new ToolSelection(deviceId, fleet, commandLogId);
        return selection.hasAnySelection() ? selection : null;
    }

    private static Long parseLongNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long v = node.asLong();
            return v > 0 ? v : null;
        }
        if (node.isTextual()) {
            String s = node.asText();
            if (!StringUtils.hasText(s)) {
                return null;
            }
            try {
                long v = Long.parseLong(s.trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ToolSelection(Long deviceId, boolean fleet, Long commandLogId) {
        boolean hasAnySelection() {
            return fleet || deviceId != null || commandLogId != null;
        }

        AssistantSelectionTokenParser.ParseResult toTokenLikeSelection(String cleanedText) {
            return new AssistantSelectionTokenParser.ParseResult(cleanedText, deviceId, fleet, commandLogId);
        }
    }
}
