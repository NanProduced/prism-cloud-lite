package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.application.audit.AssistantAuditEventPublisher;
import nan.produced.prism.core.assistant.application.audit.AssistantChatTokenAuditEvent;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import nan.produced.prism.core.common.util.TraceUtils;
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
    private final AssistantAuditEventPublisher auditEventPublisher;

    public void streamAndRespond(UUID userId,
                                 List<AssistantChatMessage> messages,
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
                        maxCompletionTokens != null ? maxCompletionTokens : 0
                ),
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

        publishTokenAuditEvent(userId, llmResult);

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

    private void publishTokenAuditEvent(UUID userId, AssistantChatLlmClient.StreamResult llmResult) {
        if (llmResult == null) {
            return;
        }

        try {
            String traceId = TraceUtils.getTraceId();
            if ("unknown".equalsIgnoreCase(traceId)) {
                traceId = null;
            }

            boolean hasTokenData = llmResult.promptTokens() != null
                    || llmResult.completionTokens() != null
                    || llmResult.totalTokens() != null;

            if (!hasTokenData) {
                return;
            }

            AssistantChatTokenAuditEvent event = AssistantChatTokenAuditEvent.create(
                    userId,
                    traceId,
                    llmResult.promptTokens(),
                    llmResult.completionTokens(),
                    llmResult.totalTokens(),
                    llmResult.model(),
                    llmResult.provider()
            );

            auditEventPublisher.publishTokenUsageEvent(event);

            log.debug("Published token audit event: userId={}, totalTokens={}, provider={}",
                    userId, llmResult.totalTokens(), llmResult.provider());

        } catch (Exception e) {
            log.warn("Failed to publish token audit event", e);
        }
    }
}
