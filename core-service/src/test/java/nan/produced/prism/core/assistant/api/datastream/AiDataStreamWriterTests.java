package nan.produced.prism.core.assistant.api.datastream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiDataStreamWriterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesTextToolCallToolResultAndFinishAsLines() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AiDataStreamWriter writer = new AiDataStreamWriter(objectMapper, out);

        writer.text("hello");
        writer.toolCall("call-1", "pickDevice", Map.of("title", "请选择设备"));
        writer.toolResult("call-1", Map.of("ok", true));
        writer.data(List.of(Map.of("type", "source", "source", Map.of("id", "x"))));
        writer.finish(Map.of("finishReason", "stop"));

        String s = out.toString(StandardCharsets.UTF_8);
        assertThat(s).contains("0:\"hello\"\n");
        assertThat(s).contains("b:{");
        assertThat(s).contains("\"toolCallId\":\"call-1\"");
        assertThat(s).contains("\"toolName\":\"pickDevice\"");
        assertThat(s).contains("\"args\":{\"title\":\"请选择设备\"}");

        assertThat(s).contains("c:{");
        assertThat(s).contains("\"toolCallId\":\"call-1\"");
        assertThat(s).contains("\"result\":{\"ok\":true}");

        assertThat(s).contains("2:[");
        assertThat(s).contains("\"type\":\"source\"");
        assertThat(s).contains("\"id\":\"x\"");
        assertThat(s).contains("d:{\"finishReason\":\"stop\"}\n");
    }
}
