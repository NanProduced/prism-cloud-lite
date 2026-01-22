package nan.produced.prism.core.assistant.application.chat;

import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;

final class AssistantChatToolEventForwarder implements AssistantChatLlmClient.ToolEventListener {

    private final AiUiMessageSseWriter writer;

    AssistantChatToolEventForwarder(AiUiMessageSseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onToolInputAvailable(String toolCallId, String toolName, Object input) {
        writer.toolInputAvailable(toolCallId, toolName, input, true);
    }

    @Override
    public void onToolOutputAvailable(String toolCallId, Object output) {
        writer.toolOutputAvailable(toolCallId, output, true);
    }

    @Override
    public void onToolOutputError(String toolCallId, String errorText) {
        writer.toolOutputError(toolCallId, errorText, true);
    }
}
