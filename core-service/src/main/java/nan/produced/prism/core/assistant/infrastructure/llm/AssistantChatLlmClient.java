package nan.produced.prism.core.assistant.infrastructure.llm;

import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AssistantChatLlmClient {

    record StreamOptions(
            int maxToolRounds,
            int maxCallsPerRound,
            int maxToolResultChars,
            int maxCompletionTokens
    ) {
    }

    record StreamResult(
            String finishReason,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }

    interface ToolEventListener {
        void onToolInputAvailable(String toolCallId, String toolName, Object input);

        void onToolOutputAvailable(String toolCallId, Object output);

        void onToolOutputError(String toolCallId, String errorText);
    }

    StreamResult stream(UUID userId, List<Message> messages, StreamOptions options, ToolEventListener toolEvents, Consumer<String> onDelta);
}
