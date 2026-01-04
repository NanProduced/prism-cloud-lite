package nan.produced.prism.core.assistant.infrastructure.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAssistantChatLlmClientToolCallParsingTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SpringAiAssistantChatLlmClient newClient() {
        return new SpringAiAssistantChatLlmClient(
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null
        );
    }

    @Test
    void parseToolCallsFromText_parsesSingleCall() {
        var client = newClient();

        var calls = client.parseToolCallsFromText("""
                <tool_call> {"name": "analyzeOfflineDevices", "arguments": {"limit": 50}} </tool_call>
                """, 10);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).toolCallId()).isNotBlank();
        assertThat(calls.get(0).toolName()).isEqualTo("analyzeOfflineDevices");
        assertThat(calls.get(0).input().get("limit").asInt()).isEqualTo(50);
    }

    @Test
    void parseToolCallsFromText_supportsMarkdownCodeFence() {
        var client = newClient();

        var calls = client.parseToolCallsFromText("""
                <tool_call>
                ```json
                {"name":"analyzeOfflineDevices","arguments":{"limit":50}}
                ```
                </tool_call>
                """, 10);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).toolName()).isEqualTo("analyzeOfflineDevices");
        assertThat(calls.get(0).input().get("limit").asInt()).isEqualTo(50);
    }

    @Test
    void parseToolCallsFromText_respectsMaxCallsPerRound() {
        var client = newClient();

        var calls = client.parseToolCallsFromText("""
                <tool_call> {"name":"a","arguments":{}} </tool_call>
                <tool_call> {"name":"b","arguments":{}} </tool_call>
                """, 1);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).toolName()).isEqualTo("a");
    }

    @Test
    void stripToolCallBlocks_removesTagBlocks() {
        var cleaned = SpringAiAssistantChatLlmClient.stripToolCallBlocks("""
                Hello
                <tool_call> {"name":"a","arguments":{}} </tool_call>
                World
                """);

        assertThat(cleaned).doesNotContain("<tool_call>");
        assertThat(cleaned).doesNotContain("</tool_call>");
        assertThat(cleaned).contains("Hello");
        assertThat(cleaned).contains("World");
    }
}

