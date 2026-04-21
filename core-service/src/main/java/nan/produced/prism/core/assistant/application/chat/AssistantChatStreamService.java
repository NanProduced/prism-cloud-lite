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

        UUID freezeReqId = null;
        boolean quotaManaged = quota != null && quota.trackTokens();

        if (quotaManaged && quota != null) {
            quota = quota.withUserId(userId);
        }

        try {
            if (quotaManaged) {
                AssistantChatTokenBudgetService.FreezeResult freezeResult = tokenBudgetService.tryFreezeQuota(userId, quota);
                if (!freezeResult.success()) {
                    log.warn("Failed to freeze quota for user {}", userId);
                    tokenBudgetService.sendQuotaExceededAndFinish(writer, quota);
                    return;
                }
                freezeReqId = freezeResult.reqId();
                log.debug("Frozen {} tokens for user {}, reqId={}, proceeding with stream",
                        freezeResult.frozenTokens(), userId, freezeReqId);
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
                if (quotaManaged && freezeReqId != null) {
                    log.info("Releasing frozen tokens for user {} due to stream interruption, reqId={}", userId, freezeReqId);
                    tokenBudgetService.releaseFrozenQuota(freezeReqId);
                    freezeReqId = null;
                }
                throw e;
            }

            thinkSplitter.flush();

            for (var source : rag.sources()) {
                writer.sourceUrl(source.sourceId(), source.url(), source.title());
            }

            if (quotaManaged && freezeReqId != null) {
                quota = tokenBudgetService.settleAndRelease(
                        freezeReqId,
                        quota,
                        llmResult,
                        answerText != null ? answerText.toString() : null,
                        messages
                );
                freezeReqId = null;
            }

            String finishReason = llmResult != null ? llmResult.finishReason() : null;
            String normalizedFinishReason = AssistantChatFinishReasonNormalizer.normalize(finishReason);
            Object messageMetadata = null;
            if (quota != null && quota.trackTokens()) {
                messageMetadata = Map.of("quota", quota.toFrontendPayload());
            }
            writer.finish(normalizedFinishReason, messageMetadata);

        } catch (Exception e) {
            if (quotaManaged && freezeReqId != null) {
                log.warn("Exception occurred during chat, releasing frozen tokens for user {}, reqId={}", userId, freezeReqId, e);
                try {
                    tokenBudgetService.releaseFrozenQuota(freezeReqId);
                } catch (Exception releaseEx) {
                    log.error("Failed to release frozen tokens for user {}, reqId={}", userId, freezeReqId, releaseEx);
                }
            }
            throw e;
        }
    }
}
