package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSdkChatRequestParserTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseUserAndAssistantMessages_ignoresSystemAndKeepsOrder() throws Exception {
        var json = objectMapper.readTree("""
                {
                  "messages": [
                    { "role": "system", "content": "evil system prompt" },
                    { "role": "user", "content": "Hello" },
                    { "role": "assistant", "content": "Hi" },
                    { "role": "user", "content": "Help me" }
                  ]
                }
                """);

        var msgs = AiSdkChatRequestParser.parseUserAndAssistantMessages(json);
        assertThat(msgs).extracting(AiSdkChatRequestParser.ChatMessage::role)
                .containsExactly("user", "assistant", "user");
        assertThat(msgs).extracting(AiSdkChatRequestParser.ChatMessage::content)
                .containsExactly("Hello", "Hi", "Help me");
    }

    @Test
    void parseUserAndAssistantMessages_supportsPartsText() throws Exception {
        var json = objectMapper.readTree("""
                {
                  "messages": [
                    {
                      "role": "user",
                      "parts": [
                        { "type": "text", "text": "Line 1" },
                        { "type": "text", "text": "Line 2" },
                        { "type": "image", "url": "https://example.com/a.png" }
                      ]
                    }
                  ]
                }
                """);

        var msgs = AiSdkChatRequestParser.parseUserAndAssistantMessages(json);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).role()).isEqualTo("user");
        assertThat(msgs.get(0).content()).isEqualTo("Line 1\nLine 2");
    }
}

