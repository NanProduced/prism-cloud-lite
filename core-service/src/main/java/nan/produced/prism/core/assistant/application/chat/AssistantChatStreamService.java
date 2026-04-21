package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatStreamService {

    private final AssistantChatLlmClient llmClient;
    private final AssistantChatTokenBudgetService tokenBudgetService;

    public void streamAndRespond(UUID userId,
                                 List<AssistantChatMessage> messages,
                                 Integer maxCompletionTokens,
                                 AssistantRagContextService.RagContext rag,
                                 AssistantChatTokenBudgetService.QuotaSnapshot quota,
                                 AiUiMessageSseWriter writer) {

        long frozenTokens = 0;
        boolean quotaManaged = quota != null && quota.trackTokens();

        try {
            if (quotaManaged) {
                AssistantChatTokenBudgetService.FreezeResult freezeResult = tokenBudgetService.tryFreezeQuota(userId, quota);
                if (!freezeResult.success()) {
                    log.warn("Failed to freeze quota for user {}", userId);
                    tokenBudgetService.sendQuotaExceededAndFinish(writer, quota);
                    return;
                }
                frozenTokens = freezeResult.frozenTokens();
                log.debug("Frozen {} tokens for user {}, proceeding with stream", frozenTokens, userId);
            }

            StringBuilder answerText = quotaManaged ? new StringBuilder() : null;
            ThinkTagStreamSplitter thinkSplitter = new ThinkTagStreamSplitter(writer);
            AssistantChatLlmClient.StreamResult llmResult = null;

            try {
                llmResult = llmClient.stream(
                        userId,
                        messages,
                        new AssistantChatLlmClient.StreamOptions(
                                maxCompletionTokens != null ? maxCompletionTokens : 0
                        ),
                        delta -> {
                            if (answerText != null && delta != null) {
                                answerText.append(delta);
                            }
                            thinkSplitter.accept(delta);
                        }
                );
            } catch (Exception e) {
                log.warn("LLM stream interrupted for user {}: {}", userId, e.getMessage());
                if (quotaManaged && frozenTokens > 0) {
                    log.info("Releasing {} frozen tokens for user {} due to stream interruption", frozenTokens, userId);
                    tokenBudgetService.releaseFrozenQuota(userId, quota, frozenTokens);
                    frozenTokens = 0;
                }
                throw e;
            }

            thinkSplitter.flush();

            for (var source : rag.sources()) {
                writer.sourceUrl(source.sourceId(), source.url(), source.title());
            }

            if (quotaManaged) {
                quota = tokenBudgetService.settleAndRelease(
                        userId,
                        quota,
                        frozenTokens,
                        llmResult,
                        answerText != null ? answerText.toString() : null,
                        messages
                );
                frozenTokens = 0;
            } else {
                quota = tokenBudgetService.trackUsage(
                        userId,
                        quota,
                        llmResult,
                        answerText != null ? answerText.toString() : null,
                        messages
                );
            }

            String finishReason = llmResult != null ? llmResult.finishReason() : null;
            String normalizedFinishReason = AssistantChatFinishReasonNormalizer.normalize(finishReason);
            Object messageMetadata = null;
            if (quota != null && quota.trackTokens()) {
                messageMetadata = Map.of("quota", quota.toFrontendPayload());
            }
            writer.finish(normalizedFinishReason, messageMetadata);

        } catch (Exception e) {
            if (quotaManaged && frozenTokens > 0) {
                log.warn("Exception occurred during chat, releasing {} frozen tokens for user {}", frozenTokens, userId, e);
                try {
                    tokenBudgetService.releaseFrozenQuota(userId, quota, frozenTokens);
                } catch (Exception releaseEx) {
                    log.error("Failed to release frozen tokens for user {}", userId, releaseEx);
                }
            }
            throw e;
        }
    }
}
