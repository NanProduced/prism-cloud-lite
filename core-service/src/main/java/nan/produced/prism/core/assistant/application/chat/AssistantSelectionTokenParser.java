package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AssistantSelectionTokenParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[\\[([^\\]]+)]]");

    public record ParseResult(String cleanedText, Long deviceId, boolean fleet, Long commandLogId) {

        boolean hasAnySelection() {
            return fleet || deviceId != null || commandLogId != null;
        }

        AssistantToolCall toInjectedToolCall(ObjectMapper objectMapper) {
            if (!hasAnySelection() || objectMapper == null) {
                return null;
            }
            ObjectNode input = objectMapper.createObjectNode();
            String toolName;
            if (commandLogId != null) {
                toolName = "diagnoseDeviceCommand";
                input.put("commandLogId", commandLogId);
            } else if (deviceId != null) {
                toolName = "diagnoseDevice";
                input.put("deviceId", deviceId);
            } else if (fleet) {
                toolName = "analyzeOfflineDevices";
                input.put("limit", 50);
            } else {
                return null;
            }
            return new AssistantToolCall("tool-" + UUID.randomUUID(), toolName, input);
        }
    }

    public ParseResult parse(String text) {
        if (!StringUtils.hasText(text)) {
            return new ParseResult(text, null, false, null);
        }

        Long deviceId = null;
        Long commandLogId = null;
        boolean fleet = false;

        StringBuilder out = new StringBuilder(text.length());
        Matcher m = TOKEN_PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (start > last) {
                out.append(text, last, start);
            }

            String inner = m.group(1);
            Token token = parseKnownToken(inner);
            if (token != null) {
                if (token.deviceId() != null) {
                    deviceId = token.deviceId();
                }
                if (token.commandLogId() != null) {
                    commandLogId = token.commandLogId();
                }
                if (token.fleet()) {
                    fleet = true;
                }
            } else {
                out.append(text, start, end);
            }
            last = end;
        }
        if (last < text.length()) {
            out.append(text, last, text.length());
        }

        String cleaned = normalizeSpaces(out.toString());
        return new ParseResult(cleaned, deviceId, fleet, commandLogId);
    }

    public String stripKnownTokens(String text) {
        return parse(text).cleanedText();
    }

    private record Token(Long deviceId, boolean fleet, Long commandLogId) {
    }

    private static Token parseKnownToken(String inner) {
        if (!StringUtils.hasText(inner)) {
            return null;
        }
        String trimmed = inner.trim();

        // Supported tokens:
        // - [[deviceId:123]]
        // - [[commandLogId:456]]
        // - [[fleet:true]]
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith("deviceid:")) {
            Long id = parseLongSafe(trimmed.substring("deviceId:".length()));
            return id != null ? new Token(id, false, null) : null;
        }
        if (lower.startsWith("commandlogid:")) {
            Long id = parseLongSafe(trimmed.substring("commandLogId:".length()));
            return id != null ? new Token(null, false, id) : null;
        }
        if (lower.startsWith("fleet:")) {
            String v = trimmed.substring("fleet:".length()).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(v) || "1".equals(v) || "yes".equals(v)) {
                return new Token(null, true, null);
            }
            if ("false".equals(v) || "0".equals(v) || "no".equals(v)) {
                return new Token(null, false, null);
            }
            return null;
        }

        return null;
    }

    private static Long parseLongSafe(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeSpaces(String s) {
        if (!StringUtils.hasText(s)) {
            return s;
        }
        String out = s.replace('\u00A0', ' ');
        out = out.replaceAll("[ \\t]+", " ");
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out.trim();
    }
}
