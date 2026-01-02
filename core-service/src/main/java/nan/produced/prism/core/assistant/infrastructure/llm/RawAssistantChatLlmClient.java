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
    public StreamResult stream(UUID userId, List<Message> messages, StreamOptions options, ToolEventListener toolEvents, Consumer<String> onDelta) {
        Integer maxCompletionTokens = options != null && options.maxCompletionTokens() > 0 ? options.maxCompletionTokens() : null;
        OpenAiChatCompletionsClient.ChatResult result = rawClient.complete(messages, maxCompletionTokens);
        String answer = result != null ? result.content() : null;
        if (answer != null && !answer.isBlank()) {
            onDelta.accept(answer);
        }
        String finishReason = result != null ? result.finishReason() : null;
        return new StreamResult(
                finishReason,
                result != null ? result.promptTokens() : null,
                result != null ? result.completionTokens() : null,
                result != null ? result.totalTokens() : null
        );
    }
}
