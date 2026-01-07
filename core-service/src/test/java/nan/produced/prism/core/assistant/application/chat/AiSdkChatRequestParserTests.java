package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSdkChatRequestParserTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lastToolMessage_returnsToolCallIdAndContent() throws Exception {
        var request = objectMapper.readTree("""
                {
                  "messages": [
                    { "role": "user", "content": "hi" },
                    { "role": "tool", "tool_call_id": "call_123", "content": "{\\"deviceId\\":\\"1\\"}" }
                  ]
                }
                """);

        var tool = AiSdkChatRequestParser.lastToolMessage(request);

        assertThat(tool).isNotNull();
        assertThat(tool.toolCallId()).isEqualTo("call_123");
        assertThat(tool.content()).contains("deviceId");
    }

    @Test
    void lastNonSystemRole_ignoresSystem() throws Exception {
        var request = objectMapper.readTree("""
                {
                  "messages": [
                    { "role": "system", "content": "ignore" },
                    { "role": "user", "content": "hi" }
                  ]
                }
                """);

        assertThat(AiSdkChatRequestParser.lastNonSystemRole(request)).isEqualTo("user");
    }
}

