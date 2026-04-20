package nan.produced.prism.core.assistant.infrastructure.llm;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AssistantChatLlmClient {

    record StreamOptions(
            int maxCompletionTokens
    ) {
    }

    record StreamResult(
            String finishReason,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String provider,
            String model
    ) {
    }

    StreamResult stream(UUID userId, List<AssistantChatMessage> messages, StreamOptions options, Consumer<String> onDelta);
}
