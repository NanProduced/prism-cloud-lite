package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;

public final class AiSdkChatRequestParser {

    private AiSdkChatRequestParser() {
    }

    public static String lastUserText(JsonNode request) {
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
            String role = textOrNull(msg.get("role"));
            if (!"user".equalsIgnoreCase(role)) {
                continue;
            }

            String content = textOrNull(msg.get("content"));
            if (content != null && !content.isBlank()) {
                return content.trim();
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
                if (!sb.isEmpty()) {
                    return sb.toString().trim();
                }
            }
        }

        return null;
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}

