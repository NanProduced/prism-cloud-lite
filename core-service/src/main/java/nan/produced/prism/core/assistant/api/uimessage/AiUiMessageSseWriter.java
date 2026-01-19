package nan.produced.prism.core.assistant.api.uimessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class AiUiMessageSseWriter {

    private final ObjectMapper objectMapper;
    private final OutputStream outputStream;

    private boolean startedStep;
    private boolean textStarted;
    private boolean reasoningStarted;

    public void startStep() {
        if (startedStep) {
            return;
        }
        writeData(Map.of("type", "start-step"));
        startedStep = true;
    }

    public void textDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        ensureTextStarted();
        writeData(Map.of(
                "type", "text-delta",
                "id", "text-1",
                "delta", delta
        ));
    }

    public void reasoningDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        ensureReasoningStarted();
        writeData(Map.of(
                "type", "reasoning-delta",
                "id", "reasoning-1",
                "delta", delta
        ));
    }

    public void toolInputAvailable(String toolCallId, String toolName, Object input) {
        toolInputAvailable(toolCallId, toolName, input, false);
    }

    /**
     * 工具输入可用
     * @param toolCallId 工具调用id
     * @param toolName 工具名称
     * @param input 工具调用输入
     * @param providerExecuted 是否由提供者执行
     */
    public void toolInputAvailable(String toolCallId, String toolName, Object input, boolean providerExecuted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool-input-available");
        payload.put("toolCallId", toolCallId);
        payload.put("toolName", toolName);
        payload.put("input", input == null ? Map.of() : input);
        if (providerExecuted) {
            payload.put("providerExecuted", true);
        }
        writeData(payload);
    }

    public void toolInputError(String toolCallId, String toolName, Object input, String errorText) {
        toolInputError(toolCallId, toolName, input, errorText, false);
    }

    public void toolInputError(String toolCallId, String toolName, Object input, String errorText, boolean providerExecuted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool-input-error");
        payload.put("toolCallId", toolCallId);
        payload.put("toolName", toolName);
        payload.put("input", input == null ? Map.of() : input);
        payload.put("errorText", errorText == null ? "" : errorText);
        if (providerExecuted) {
            payload.put("providerExecuted", true);
        }
        writeData(payload);
    }

    public void toolOutputAvailable(String toolCallId, Object output) {
        toolOutputAvailable(toolCallId, output, false);
    }

    /**
     * 工具输出可用
     * @param toolCallId 工具调用id
     * @param output 工具调用结果
     * @param providerExecuted 是否由提供者执行
     */
    public void toolOutputAvailable(String toolCallId, Object output, boolean providerExecuted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool-output-available");
        payload.put("toolCallId", toolCallId);
        payload.put("output", output == null ? Map.of() : output);
        if (providerExecuted) {
            payload.put("providerExecuted", true);
        }
        writeData(payload);
    }

    public void toolOutputError(String toolCallId, String errorText) {
        toolOutputError(toolCallId, errorText, false);
    }

    public void toolOutputError(String toolCallId, String errorText, boolean providerExecuted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool-output-error");
        payload.put("toolCallId", toolCallId);
        payload.put("errorText", errorText == null ? "" : errorText);
        if (providerExecuted) {
            payload.put("providerExecuted", true);
        }
        writeData(payload);
    }

    public void sourceUrl(String sourceId, String url, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "source-url");
        payload.put("sourceId", sourceId != null ? sourceId : (url != null ? url : "unknown"));
        payload.put("url", url != null ? url : "");
        if (title != null && !title.isBlank()) {
            payload.put("title", title);
        }
        writeData(payload);
    }

    public void data(String name, Object data) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("data name is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "data-" + name.trim());
        payload.put("data", data == null ? Map.of() : data);
        writeData(payload);
    }

    public void error(String errorText) {
        writeData(Map.of(
                "type", "error",
                "errorText", errorText == null ? "" : errorText
        ));
    }

    public void finish(String finishReason, Object messageMetadata) {
        finishStepIfNeeded();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "finish");
        if (finishReason != null && !finishReason.isBlank()) {
            payload.put("finishReason", finishReason);
        }
        if (messageMetadata != null) {
            payload.put("messageMetadata", messageMetadata);
        }
        writeData(payload);

        writeDone();
    }

    private void ensureTextStarted() {
        if (textStarted) {
            return;
        }
        writeData(Map.of("type", "text-start", "id", "text-1"));
        textStarted = true;
    }

    private void ensureReasoningStarted() {
        if (reasoningStarted) {
            return;
        }
        writeData(Map.of("type", "reasoning-start", "id", "reasoning-1"));
        reasoningStarted = true;
    }

    private void finishStepIfNeeded() {
        if (textStarted) {
            writeData(Map.of("type", "text-end", "id", "text-1"));
            textStarted = false;
        }
        if (reasoningStarted) {
            writeData(Map.of("type", "reasoning-end", "id", "reasoning-1"));
            reasoningStarted = false;
        }
        if (startedStep) {
            writeData(Map.of("type", "finish-step"));
            startedStep = false;
        }
    }

    /*
    SSE 是一种基于文本的协议。浏览器或前端 SDK（如 Vercel AI SDK）在读取流时，需要知道一个完整的数据块（Event Block）何时结束。
    单个 \n：用于分隔一个事件内部的不同字段。 例如，一个事件可以包含 id、event 类型和 data：
    双个 \n\n：用于标记整个事件的结束。 只有当解析器看到一个空行（即第二个 \n）时，它才会认为“好，这个消息接收完整了”，然后将其触发给前端代码处理。
     */

    /**
     * 写入数据
     * @param payload  数据
     */
    private void writeData(Object payload) {
        try {
            String encoded = encodeJson(payload);
            String event = "data: " + encoded + "\n\n";
            outputStream.write(event.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeDone() {
        try {
            outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String encodeJson(Object payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(payload);
    }
}
