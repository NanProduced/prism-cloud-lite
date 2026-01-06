package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temporary tool planner (heuristics).
 *
 * <p>Will be replaced by Spring AI native function calling once providers are integrated.</p>
 */
@Component
@RequiredArgsConstructor
public class AssistantToolPlanner {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("\\b(\\d{3,})\\b");

    private final ObjectMapper objectMapper;

    public AssistantToolCall plan(String userText) {
        if (userText == null || userText.isBlank()) {
            return null;
        }

        // Example: "最近离线的设备有哪些？" -> analyzeOfflineDevices
        if (userText.contains("离线") && (userText.contains("设备") || userText.toLowerCase().contains("device"))) {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("limit", 50);
            return new AssistantToolCall("tool-" + UUID.randomUUID(), "analyzeOfflineDevices", input);
        }

        // Example: "设备 10001 的状态是什么？" -> getDeviceDetail
        Matcher idMatcher = DEVICE_ID_PATTERN.matcher(userText);
        if (userText.contains("设备") && idMatcher.find()) {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("deviceId", Long.parseLong(idMatcher.group(1)));
            return new AssistantToolCall("tool-" + UUID.randomUUID(), "getDeviceDetail", input);
        }

        // Example: "搜索设备 Lobby" / "找一下设备 Lobby" -> searchDevices
        if ((userText.contains("搜索") || userText.contains("查") || userText.toLowerCase().contains("search"))
                && (userText.contains("设备") || userText.toLowerCase().contains("device"))) {
            ObjectNode input = objectMapper.createObjectNode();
            String keyword = guessKeyword(userText);
            if (keyword != null && !keyword.isBlank()) {
                input.put("keyword", keyword);
            }
            input.put("limit", 20);
            return new AssistantToolCall("tool-" + UUID.randomUUID(), "searchDevices", input);
        }

        // Example: "为什么指令一直待下发 / 指令没生效？" -> diagnoseDeviceCommand (recent)
        String lowered = userText.toLowerCase();
        if ((userText.contains("指令") || userText.contains("命令") || lowered.contains("command"))
                && (userText.contains("没执行") || userText.contains("不执行") || userText.contains("没生效")
                || userText.contains("失败") || userText.contains("待下发") || lowered.contains("pending")
                || userText.contains("排查") || userText.contains("诊断"))) {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("sinceMinutes", 1440);
            input.put("limit", 10);
            return new AssistantToolCall("tool-" + UUID.randomUUID(), "diagnoseDeviceCommand", input);
        }

        // Example: "帮我查一下最近的指令日志" -> searchCommandLogs (recent)
        if ((userText.contains("指令") || userText.contains("命令") || lowered.contains("command"))
                && (userText.contains("日志") || userText.contains("记录") || lowered.contains("log"))) {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("sinceMinutes", 1440);
            input.put("limit", 10);
            return new AssistantToolCall("tool-" + UUID.randomUUID(), "searchCommandLogs", input);
        }

        return null;
    }

    private static String guessKeyword(String userText) {
        // crude extraction: take content after first space, or after "设备"
        String trimmed = userText.trim();
        int idx = trimmed.indexOf("设备");
        if (idx >= 0 && idx + 2 < trimmed.length()) {
            String tail = trimmed.substring(idx + 2).trim();
            if (!tail.isBlank()) {
                return tail;
            }
        }
        int sp = trimmed.indexOf(' ');
        if (sp >= 0 && sp + 1 < trimmed.length()) {
            String tail = trimmed.substring(sp + 1).trim();
            return tail.isBlank() ? null : tail;
        }
        return null;
    }
}
