package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistantChatStreamService {

    private final AssistantChatLlmClient llmClient;
    private final AssistantChatTokenBudgetService tokenBudgetService;

    public void streamAndRespond(UUID userId,
                                 List<Message> messages,
                                 AssistantChatTierLimits limits,
                                 Integer maxCompletionTokens,
                                 AssistantRagContextService.RagContext rag,
                                 AssistantChatTokenBudgetService.QuotaSnapshot quota,
                                 AiUiMessageSseWriter writer) {
        StringBuilder answerText = quota != null && quota.trackTokens() ? new StringBuilder() : null;
        ThinkTagStreamSplitter thinkSplitter = new ThinkTagStreamSplitter(writer);
        AssistantChatLlmClient.StreamResult llmResult = llmClient.stream(
                userId,
                messages,
                new AssistantChatLlmClient.StreamOptions(
                        limits.toolMaxRounds(),
                        limits.toolMaxCallsPerRound(),
                        limits.toolResultMaxChars(),
                        maxCompletionTokens != null ? maxCompletionTokens : 0
                ),
                new AssistantChatToolEventForwarder(writer),
                delta -> {
                    if (answerText != null && delta != null) {
                        answerText.append(delta);
                    }
                    thinkSplitter.accept(delta);
                }
        );
        thinkSplitter.flush();

        for (var source : rag.sources()) {
            writer.sourceUrl(source.sourceId(), source.url(), source.title());
        }

        quota = tokenBudgetService.trackUsage(
                userId,
                quota,
                llmResult,
                answerText != null ? answerText.toString() : null,
                messages
        );

        String finishReason = llmResult != null ? llmResult.finishReason() : null;
        String normalizedFinishReason = AssistantChatFinishReasonNormalizer.normalize(finishReason);
        Object messageMetadata = null;
        if (quota != null && quota.trackTokens()) {
            messageMetadata = Map.of("quota", quota.toFrontendPayload());
        }
        writer.finish(normalizedFinishReason, messageMetadata);
    }
}
