package nan.produced.prism.core.assistant.api.datastream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RequiredArgsConstructor
public class AiDataStreamWriter {

    private final ObjectMapper objectMapper;
    private final OutputStream outputStream;

    public void text(String delta) {
        writeLine("0", delta == null ? "" : delta);
    }

    public void reasoning(String delta) {
        writeLine("1", delta == null ? "" : delta);
    }

    public void toolCall(String toolCallId, String toolName, Object args) {
        writeLine("b", Map.of(
                "toolCallId", toolCallId,
                "toolName", toolName,
                "args", args
        ));
    }

    public void toolResult(String toolCallId, Object result) {
        writeLine("c", Map.of(
                "toolCallId", toolCallId,
                "result", result
        ));
    }

    public void data(Object data) {
        writeLine("2", data == null ? Map.of() : data);
    }

    public void error(String errorText) {
        writeLine("3", errorText == null ? "" : errorText);
    }

    public void finish(Map<String, Object> payload) {
        writeLine("d", payload == null ? Map.of() : payload);
    }

    private void writeLine(String tag, Object payload) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag is required");
        }
        try {
            String encoded = objectMapper.writeValueAsString(payload);
            String line = tag + ":" + encoded + "\n";
            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
