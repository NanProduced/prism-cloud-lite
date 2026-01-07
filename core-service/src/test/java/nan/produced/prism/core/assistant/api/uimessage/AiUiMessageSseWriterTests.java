package nan.produced.prism.core.assistant.api.uimessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiUiMessageSseWriterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesTextToolAndFinishAsSseJsonWithDoneMarker() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AiUiMessageSseWriter writer = new AiUiMessageSseWriter(objectMapper, out);

        writer.startStep();
        writer.textDelta("hello");
        writer.reasoningDelta("thinking...");
        writer.toolInputAvailable("call-1", "pickDevice", Map.of("title", "请选择设备"));
        writer.toolOutputAvailable("call-1", Map.of("ok", true));
        writer.sourceUrl("src-1", "https://example.com", "Example");
        writer.finish("stop", Map.of("quota", Map.of("tier", "FREE")));

        String s = out.toString(StandardCharsets.UTF_8);

        assertThat(s).endsWith("data: [DONE]\n\n");

        List<JsonNode> events = parseSseJsonEvents(s);

        assertThat(events).anySatisfy(n -> assertThat(n.get("type").asText()).isEqualTo("start-step"));

        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("text-start");
            assertThat(n.get("id").asText()).isEqualTo("text-1");
        });
        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("text-delta");
            assertThat(n.get("id").asText()).isEqualTo("text-1");
            assertThat(n.get("delta").asText()).isEqualTo("hello");
        });
        assertThat(events).anySatisfy(n -> assertThat(n.get("type").asText()).isEqualTo("text-end"));

        assertThat(events).anySatisfy(n -> assertThat(n.get("type").asText()).isEqualTo("reasoning-start"));
        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("reasoning-delta");
            assertThat(n.get("delta").asText()).isEqualTo("thinking...");
        });
        assertThat(events).anySatisfy(n -> assertThat(n.get("type").asText()).isEqualTo("reasoning-end"));

        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("tool-input-available");
            assertThat(n.get("toolCallId").asText()).isEqualTo("call-1");
            assertThat(n.get("toolName").asText()).isEqualTo("pickDevice");
            assertThat(n.get("input").get("title").asText()).isEqualTo("请选择设备");
        });
        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("tool-output-available");
            assertThat(n.get("toolCallId").asText()).isEqualTo("call-1");
            assertThat(n.get("output").get("ok").asBoolean()).isTrue();
        });

        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("source-url");
            assertThat(n.get("sourceId").asText()).isEqualTo("src-1");
            assertThat(n.get("url").asText()).isEqualTo("https://example.com");
            assertThat(n.get("title").asText()).isEqualTo("Example");
        });

        assertThat(events).anySatisfy(n -> assertThat(n.get("type").asText()).isEqualTo("finish-step"));
        assertThat(events).anySatisfy(n -> {
            assertThat(n.get("type").asText()).isEqualTo("finish");
            assertThat(n.get("finishReason").asText()).isEqualTo("stop");
            assertThat(n.get("messageMetadata").get("quota").get("tier").asText()).isEqualTo("FREE");
        });
    }

    private List<JsonNode> parseSseJsonEvents(String sse) {
        List<JsonNode> events = new ArrayList<>();
        for (String block : sse.split("\\n\\n")) {
            String line = block.trim();
            if (line.isEmpty() || !line.startsWith("data: ")) {
                continue;
            }
            String data = line.substring("data: ".length()).trim();
            if ("[DONE]".equals(data)) {
                continue;
            }
            try {
                events.add(objectMapper.readTree(data));
            } catch (Exception e) {
                throw new AssertionError("invalid json event: " + data, e);
            }
        }
        return events;
    }
}
