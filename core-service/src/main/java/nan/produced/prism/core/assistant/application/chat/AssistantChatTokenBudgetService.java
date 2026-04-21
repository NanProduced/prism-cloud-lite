package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatTokenBudgetProperties;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantChatTokenUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatTokenBudgetService {

    private final AssistantChatTokenBudgetProperties tokenBudget;
    private final AssistantChatTokenUsageRepository tokenUsageRepository;

    public boolean isEnabled() {
        return tokenBudget != null && tokenBudget.isEnabled();
    }

    public ZoneId resetZoneId() {
        return tokenBudget != null ? tokenBudget.resetZoneId() : ZoneId.of("UTC");
    }

    public QuotaSnapshot resolveQuotaSnapshot(UUID userId, String tier, List<AssistantChatMessage> messages, ZoneId zoneId) {
        String tierKey = normalizeTierOrFree(tier);
        long dailyLimit = tokenBudget.getDailyLimitOrUnlimited(tierKey);
        if (dailyLimit <= 0) {
            return QuotaSnapshot.unlimited();
        }

        LocalDate day = LocalDate.now(zoneId);
        long used = tokenUsageRepository.getUsedTokens(userId, day);
        long frozen = tokenUsageRepository.getActiveFrozenTokens(userId, day);
        long totalCommitted = used + frozen;
        long remaining = Math.max(0, dailyLimit - totalCommitted);
        OffsetDateTime resetAt = day.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

        int configuredMaxCompletion = tokenBudget.getMaxCompletionTokensOrDefault(tierKey);
        int estimatedPrompt = estimatePromptTokens(messages);

        if (remaining <= 0) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, frozen, resetAt);
        }

        long estimatedTotalForThisRequest = estimatedPrompt + Math.max(0, configuredMaxCompletion);
        if (remaining < estimatedTotalForThisRequest) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, frozen, resetAt);
        }

        int maxCompletionTokens = configuredMaxCompletion > 0 ? configuredMaxCompletion : (int) Math.min(Integer.MAX_VALUE, remaining - estimatedPrompt);

        return QuotaSnapshot.allowed(
                day,
                tierKey,
                dailyLimit,
                used,
                frozen,
                resetAt,
                maxCompletionTokens,
                estimatedPrompt,
                configuredMaxCompletion
        );
    }

    public FreezeResult tryFreezeQuota(UUID userId, QuotaSnapshot quota) {
        if (quota == null || !quota.trackTokens() || quota.blocked() || quota.frozenTokensForThisRequest() <= 0) {
            return FreezeResult.notRequired();
        }

        Optional<UUID> reqIdOpt = tokenUsageRepository.tryFreezeTokens(
                userId,
                quota.day(),
                quota.frozenTokensForThisRequest(),
                quota.dailyLimit()
        );

        if (reqIdOpt.isPresent()) {
            UUID reqId = reqIdOpt.get();
            log.debug("Frozen {} tokens for user {} on {} (dailyLimit={}), reqId={}",
                    quota.frozenTokensForThisRequest(), userId, quota.day(), quota.dailyLimit(), reqId);
            return FreezeResult.success(reqId, quota.frozenTokensForThisRequest());
        } else {
            log.warn("Failed to freeze {} tokens for user {} on {} (dailyLimit={}), concurrent request may have consumed quota",
                    quota.frozenTokensForThisRequest(), userId, quota.day(), quota.dailyLimit());
            return FreezeResult.failed();
        }
    }

    public void releaseFrozenQuota(UUID reqId) {
        if (reqId == null) {
            return;
        }
        tokenUsageRepository.releaseFrozenTokens(reqId);
    }

    public QuotaSnapshot settleAndRelease(UUID reqId,
                                            QuotaSnapshot quota,
                                            AssistantChatLlmClient.StreamResult llmResult,
                                            String answerText,
                                            List<AssistantChatMessage> messages) {
        if (quota == null || !quota.trackTokens() || reqId == null) {
            return quota;
        }

        long actualTokensUsed;
        if (llmResult != null && llmResult.totalTokens() != null && llmResult.totalTokens() > 0) {
            actualTokensUsed = llmResult.totalTokens();
        } else {
            actualTokensUsed = Math.max(1, estimatePromptTokens(messages) + estimateTokensFromText(answerText));
        }

        tokenUsageRepository.settleAndRelease(reqId, actualTokensUsed);
        log.debug("Settled {} tokens for reqId={}, user={}, day={}",
                actualTokensUsed, reqId, quota.day(), quota.dailyLimit());

        long usedAfter = tokenUsageRepository.getUsedTokens(quota.userId() != null ? quota.userId() : UUID.randomUUID(), quota.day());
        long frozenAfter = tokenUsageRepository.getActiveFrozenTokens(quota.userId() != null ? quota.userId() : UUID.randomUUID(), quota.day());
        return quota.withUsedAndFrozenTokens(usedAfter, frozenAfter);
    }

    public int cleanupExpiredFrozenTokens(Duration maxAge) {
        int cleaned = tokenUsageRepository.cleanupExpiredFrozenTokens(maxAge);
        if (cleaned > 0) {
            log.warn("Cleaned up {} expired frozen token records (age > {})", cleaned, maxAge);
        }
        return cleaned;
    }

    public void sendQuotaExceededAndFinish(AiUiMessageSseWriter writer, QuotaSnapshot quota) {
        if (writer == null || quota == null) {
            return;
        }
        writer.error("今日 AI 助手体验额度已用完（" + quota.tier() + "）。请明天再试，或升级到 Pro，或绑定自己的 API Key。");
        writer.finish("stop", Map.of(
                "errorCode", "ASSISTANT_TOKEN_DAILY_LIMIT_EXCEEDED",
                "details", quota.toFrontendPayload()
        ));
    }

    private static int estimatePromptTokens(List<AssistantChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (AssistantChatMessage m : messages) {
            if (m == null || m.content() == null) {
                continue;
            }
            total += estimateTokensFromText(m.content());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static long estimateTokensFromText(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        long cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                cjk++;
            }
        }
        long nonCjk = text.length() - cjk;
        long approxEnglishTokens = (nonCjk + 3) / 4;
        return cjk + approxEnglishTokens;
    }

    private static String normalizeTierOrFree(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        return "PRO".equals(upper) ? "PRO" : "FREE";
    }

    public record FreezeResult(
            boolean required,
            boolean success,
            UUID reqId,
            long frozenTokens
    ) {
        public static FreezeResult notRequired() {
            return new FreezeResult(false, true, null, 0);
        }

        public static FreezeResult success(UUID reqId, long tokens) {
            return new FreezeResult(true, true, reqId, tokens);
        }

        public static FreezeResult failed() {
            return new FreezeResult(true, false, null, 0);
        }
    }

    public record QuotaSnapshot(
            boolean trackTokens,
            boolean blocked,
            LocalDate day,
            String tier,
            long dailyLimit,
            long usedTokens,
            long frozenTokens,
            OffsetDateTime resetAt,
            Integer maxCompletionTokensForThisRequest,
            Integer estimatedPromptTokens,
            Integer maxCompletionConfigured,
            UUID userId
    ) {
        public long frozenTokensForThisRequest() {
            if (!trackTokens || blocked || estimatedPromptTokens == null || maxCompletionConfigured == null) {
                return 0;
            }
            return (long) estimatedPromptTokens + Math.max(0, maxCompletionConfigured);
        }

        static QuotaSnapshot unlimited() {
            return new QuotaSnapshot(false, false, LocalDate.now(ZoneOffset.UTC), "PRO", 0, 0, 0, OffsetDateTime.now(ZoneOffset.UTC), null, null, null, null);
        }

        static QuotaSnapshot blocked(LocalDate day, String tier, long dailyLimit, long usedTokens, long frozenTokens, OffsetDateTime resetAt) {
            return new QuotaSnapshot(true, true, day, tier, dailyLimit, usedTokens, frozenTokens, resetAt, 0, 0, 0, null);
        }

        static QuotaSnapshot allowed(LocalDate day, String tier, long dailyLimit, long usedTokens, long frozenTokens, OffsetDateTime resetAt, int maxCompletionTokens, int estimatedPrompt, int maxCompletionConfigured) {
            return new QuotaSnapshot(true, false, day, tier, dailyLimit, usedTokens, frozenTokens, resetAt, maxCompletionTokens, estimatedPrompt, maxCompletionConfigured, null);
        }

        public QuotaSnapshot withUserId(UUID userId) {
            return new QuotaSnapshot(trackTokens, blocked, day, tier, dailyLimit, usedTokens, frozenTokens, resetAt, maxCompletionTokensForThisRequest, estimatedPromptTokens, maxCompletionConfigured, userId);
        }

        QuotaSnapshot withUsedTokens(long usedTokens) {
            return new QuotaSnapshot(trackTokens, blocked, day, tier, dailyLimit, usedTokens, frozenTokens, resetAt, maxCompletionTokensForThisRequest, estimatedPromptTokens, maxCompletionConfigured, userId);
        }

        QuotaSnapshot withUsedAndFrozenTokens(long usedTokens, long frozenTokens) {
            return new QuotaSnapshot(trackTokens, blocked, day, tier, dailyLimit, usedTokens, frozenTokens, resetAt, maxCompletionTokensForThisRequest, estimatedPromptTokens, maxCompletionConfigured, userId);
        }

        Map<String, Object> toFrontendPayload() {
            long totalCommitted = usedTokens + frozenTokens;
            long remaining = dailyLimit > 0 ? Math.max(0, dailyLimit - totalCommitted) : -1;
            return Map.of(
                    "tier", tier,
                    "day", day.toString(),
                    "dailyLimit", dailyLimit,
                    "usedTokens", usedTokens,
                    "frozenTokens", frozenTokens,
                    "remainingTokens", remaining,
                    "resetAt", resetAt != null ? resetAt.toString() : null,
                    "provider", "local-vllm"
            );
        }
    }
}
