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
        writeData(Map.of(
                "type", "tool-input-available",
                "toolCallId", toolCallId,
                "toolName", toolName,
                "input", input == null ? Map.of() : input
        ));
    }

    public void toolInputError(String toolCallId, String toolName, Object input, String errorText) {
        writeData(Map.of(
                "type", "tool-input-error",
                "toolCallId", toolCallId,
                "toolName", toolName,
                "input", input == null ? Map.of() : input,
                "errorText", errorText == null ? "" : errorText
        ));
    }

    public void toolOutputAvailable(String toolCallId, Object output) {
        writeData(Map.of(
                "type", "tool-output-available",
                "toolCallId", toolCallId,
                "output", output == null ? Map.of() : output
        ));
    }

    public void toolOutputError(String toolCallId, String errorText) {
        writeData(Map.of(
                "type", "tool-output-error",
                "toolCallId", toolCallId,
                "errorText", errorText == null ? "" : errorText
        ));
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
