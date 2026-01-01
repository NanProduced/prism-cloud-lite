package nan.produced.prism.core.assistant.infrastructure.llm;

import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AssistantChatLlmClient {

    record StreamResult(String finishReason) {
    }

    interface ToolEventListener {
        void onToolInputAvailable(String toolCallId, String toolName, Object input);

        void onToolOutputAvailable(String toolCallId, Object output);

        void onToolOutputError(String toolCallId, String errorText);
    }

    StreamResult stream(UUID userId, List<Message> messages, ToolEventListener toolEvents, Consumer<String> onDelta);
}
