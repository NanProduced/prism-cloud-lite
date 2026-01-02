package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AiSdkChatRequestParser {

    private AiSdkChatRequestParser() {
    }

    public record ChatMessage(String role, String content) {
    }

    /**
     * Parses Vercel AI SDK-like request body and extracts chat messages in chronological order.
     *
     * <p>Only {@code role=user|assistant} messages are returned. Client-supplied {@code system} messages
     * are ignored intentionally to prevent prompt injection overriding server policy.</p>
     */
    public static List<ChatMessage> parseUserAndAssistantMessages(JsonNode request) {
        if (request == null) {
            return List.of();
        }

        JsonNode messages = request.get("messages");
        if (messages == null || !messages.isArray()) {
            return List.of();
        }

        List<ChatMessage> result = new ArrayList<>(Math.min(32, messages.size()));
        for (JsonNode msg : messages) {
            if (msg == null) {
                continue;
            }
            String roleRaw = textOrNull(msg.get("role"));
            if (roleRaw == null) {
                continue;
            }
            String role = roleRaw.trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }

            String content = extractMessageText(msg);
            if (content == null || content.isBlank()) {
                continue;
            }
            result.add(new ChatMessage(role, content.trim()));
        }
        return result;
    }

    public static String lastUserText(JsonNode request) {
        List<ChatMessage> parsed = parseUserAndAssistantMessages(request);
        for (int i = parsed.size() - 1; i >= 0; i--) {
            ChatMessage m = parsed.get(i);
            if ("user".equals(m.role()) && m.content() != null && !m.content().isBlank()) {
                return m.content().trim();
            }
        }
        return null;
    }

    private static String extractMessageText(JsonNode msg) {
        String content = textOrNull(msg.get("content"));
        if (content != null && !content.isBlank()) {
            return content;
        }

        JsonNode parts = msg.get("parts");
        if (parts != null && parts.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                if (part == null) {
                    continue;
                }
                String type = textOrNull(part.get("type"));
                if (!"text".equalsIgnoreCase(type)) {
                    continue;
                }
                String text = textOrNull(part.get("text"));
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(text);
            }
            return sb.isEmpty() ? null : sb.toString();
        }

        return null;
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
