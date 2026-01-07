package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AiSdkChatRequestParser {

    private AiSdkChatRequestParser() {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ToolMessage(String toolCallId, String content) {
    }

    public record ToolOutput(String toolCallId, String toolName, String state, JsonNode output, String errorText) {
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

    /**
     * Returns the last tool message in a Vercel AI SDK-like request.
     *
     * <p>Expected shape:</p>
     * <pre>
     * {"role":"tool","tool_call_id":"...","content":"{...}"}
     * </pre>
     *
     * <p>Notes:</p>
     * <ul>
     *   <li>{@code content} is expected to be a JSON string (frontend guarantees this).</li>
     *   <li>This method does not parse the JSON string, it just returns it.</li>
     * </ul>
     */
    public static ToolMessage lastToolMessage(JsonNode request) {
        if (request == null) {
            return null;
        }

        JsonNode messages = request.get("messages");
        if (messages == null || !messages.isArray()) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            if (msg == null) {
                continue;
            }
            String roleRaw = textOrNull(msg.get("role"));
            if (roleRaw == null) {
                continue;
            }
            String role = roleRaw.trim().toLowerCase(Locale.ROOT);
            if (!"tool".equals(role)) {
                continue;
            }

            String toolCallId = textOrNull(msg.get("tool_call_id"));
            String content = textOrNull(msg.get("content"));
            if (toolCallId == null || toolCallId.isBlank() || content == null || content.isBlank()) {
                continue;
            }
            return new ToolMessage(toolCallId.trim(), content.trim());
        }

        return null;
    }

    /**
     * Returns a tool output from UIMessage-style messages (AI SDK 6).
     *
     * <p>Expected shape (assistant message part):</p>
     * <pre>
     * {
     *   "type": "tool-pickDevice",
     *   "toolCallId": "call_123",
     *   "state": "output-available",
     *   "output": { ... }
     * }
     * </pre>
     */
    public static ToolOutput findToolOutput(JsonNode request, String toolCallId, String toolName) {
        if (request == null || toolCallId == null || toolCallId.isBlank()) {
            return null;
        }

        JsonNode messages = request.get("messages");
        if (messages == null || !messages.isArray()) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            if (msg == null) {
                continue;
            }
            String roleRaw = textOrNull(msg.get("role"));
            if (roleRaw == null) {
                continue;
            }
            String role = roleRaw.trim().toLowerCase(Locale.ROOT);
            if (!"assistant".equals(role)) {
                continue;
            }

            JsonNode parts = msg.get("parts");
            if (parts == null || !parts.isArray()) {
                continue;
            }

            for (int p = parts.size() - 1; p >= 0; p--) {
                JsonNode part = parts.get(p);
                if (part == null) {
                    continue;
                }
                String type = textOrNull(part.get("type"));
                if (type == null || !type.startsWith("tool-")) {
                    continue;
                }

                String partToolCallId = textOrNull(part.get("toolCallId"));
                if (!Objects.equals(toolCallId, partToolCallId)) {
                    continue;
                }

                String partToolName = type.substring("tool-".length());
                if (toolName != null && !toolName.isBlank() && !toolName.equals(partToolName)) {
                    continue;
                }

                String state = textOrNull(part.get("state"));
                JsonNode output = part.get("output");
                String errorText = textOrNull(part.get("errorText"));
                return new ToolOutput(partToolCallId, partToolName, state, output, errorText);
            }
        }

        return null;
    }

    public static String lastNonSystemRole(JsonNode request) {
        if (request == null) {
            return null;
        }
        JsonNode messages = request.get("messages");
        if (messages == null || !messages.isArray()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            if (msg == null) {
                continue;
            }
            String roleRaw = textOrNull(msg.get("role"));
            if (roleRaw == null) {
                continue;
            }
            String role = roleRaw.trim().toLowerCase(Locale.ROOT);
            if (role.isBlank() || "system".equals(role)) {
                continue;
            }
            return role;
        }
        return null;
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
