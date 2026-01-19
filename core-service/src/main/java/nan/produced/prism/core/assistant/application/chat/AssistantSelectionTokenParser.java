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

/**
 * <p>
 *     <li>让前端把选择编码进用户文本：[[deviceId:123]] / [[commandLogId:456]] / [[fleet:true]]。</li>
 *     <li>服务端解析这些 token，把选择信息提取为结构化字段，同时把 token 从用户文本中剥离。</li>
 *     <li>在 doHandle 里把这些结构化字段转换成“注入的工具调用”（AssistantToolCall），由服务端执行工具并把结果回填给模型。</li>
 * </p>
 */
@Component
public class AssistantSelectionTokenParser {

    // 扫描文本里所有形如 ... [[xxxx]] ... 的格式，取出xxxx作为inner内容再尝试识别为已知token
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[\\[([^\\]]+)]]");

    /**
     * 解析用户输入的文本，并返回一个 ParseResult 对象，该对象包含文本的“干净”版本，以及识别到的选择信息。
     * @param cleanedText 清洗Token后的文本
     * @param deviceId 设备 ID
     * @param fleet 是否查询全部设备？
     * @param commandLogId 指定命令日志 ID
     */
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

    /**
     * 解析用户输入的文本，并返回一个 ParseResult 对象，该对象包含文本的“干净”版本，以及识别到的选择信息。
     * @param text 用户输入的文本
     * @return 一个 ParseResult 对象
     */
    public ParseResult parse(String text) {
        if (!StringUtils.hasText(text)) {
            return new ParseResult(text, null, false, null);
        }

        Long deviceId = null;
        Long commandLogId = null;
        boolean fleet = false;

        // 准备 StringBuilder 用于构建剔除 Token 后的“干净”文本
        StringBuilder out = new StringBuilder(text.length());
        // 寻找所有符合 [[...]] 格式的内容
        Matcher m = TOKEN_PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            // 当前匹配到的 Token 的起始索引
            int start = m.start();
            // // 当前匹配到的 Token 的结束索引
            int end = m.end();
            // 如果当前匹配起始点 > 上一次匹配结束点，说明中间有普通文本
            if (start > last) {
                // 将两段 Token 之间的普通文字追加到输出结果中
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

    /**
     * 尝试解析已知的 token
     * @param inner token 内容
     * @return token 解析结果，如果无法解析则返回 null
     */
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
        // 处理“不换行空格”（NBSP）
        // 当你从网页、Word 文档或 PDF 中复制文本时，经常会带入这种字符。虽然它看起来像空格，但在程序逻辑和正则匹配中，它和普通空格（\u0020）是不一样的。
        String out = s.replace('\u00A0', ' ');
        // [ \t]+ 匹配一个或多个连续的普通空格或制表符（Tab）。
        out = out.replaceAll("[ \\t]+", " ");
        // \\n{3,} 匹配三个或更多连续的换行符。
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out.trim();
    }
}
