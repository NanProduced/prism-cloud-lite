package nan.produced.prism.core.assistant.infrastructure.llm;

import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class RawAssistantChatLlmClient implements AssistantChatLlmClient {

    private final OpenAiChatCompletionsClient rawClient;

    public RawAssistantChatLlmClient(OpenAiChatCompletionsClient rawClient) {
        this.rawClient = rawClient;
    }

    @Override
    public StreamResult stream(UUID userId, List<Message> messages, ToolEventListener toolEvents, Consumer<String> onDelta) {
        OpenAiChatCompletionsClient.ChatResult result = rawClient.complete(messages);
        String answer = result != null ? result.content() : null;
        if (answer != null && !answer.isBlank()) {
            onDelta.accept(answer);
        }
        String finishReason = result != null ? result.finishReason() : null;
        return new StreamResult(finishReason);
    }
}
