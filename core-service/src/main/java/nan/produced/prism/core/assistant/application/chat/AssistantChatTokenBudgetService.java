package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatTokenBudgetProperties;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantChatTokenUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        long remaining = Math.max(0, dailyLimit - used);
        OffsetDateTime resetAt = day.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

        if (remaining <= 0) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, resetAt);
        }

        int configuredMaxCompletion = tokenBudget.getMaxCompletionTokensOrDefault(tierKey);
        int estimatedPrompt = estimatePromptTokens(messages);
        long budgetForCompletion = remaining - estimatedPrompt;
        if (budgetForCompletion <= 0) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, resetAt);
        }

        int maxCompletionTokens = (int) Math.min(Integer.MAX_VALUE, budgetForCompletion);
        if (configuredMaxCompletion > 0) {
            maxCompletionTokens = Math.min(maxCompletionTokens, configuredMaxCompletion);
        }

        return QuotaSnapshot.allowed(day, tierKey, dailyLimit, used, resetAt, maxCompletionTokens);
    }

    public QuotaSnapshot trackUsage(UUID userId,
                                    QuotaSnapshot quota,
                                    AssistantChatLlmClient.StreamResult llmResult,
                                    String answerText,
                                    List<AssistantChatMessage> messages) {
        if (quota == null || !quota.trackTokens() || llmResult == null) {
            return quota;
        }
        Integer totalTokens = llmResult.totalTokens();
        long deltaTokens = totalTokens != null && totalTokens > 0
                ? totalTokens
                : Math.max(1, estimatePromptTokens(messages) + estimateTokensFromText(answerText));
        long usedAfter = tokenUsageRepository.addTokens(userId, quota.day(), deltaTokens);
        return quota.withUsedTokens(usedAfter);
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

    public record QuotaSnapshot(
            boolean trackTokens,
            boolean blocked,
            LocalDate day,
            String tier,
            long dailyLimit,
            long usedTokens,
            OffsetDateTime resetAt,
            Integer maxCompletionTokensForThisRequest
    ) {
        static QuotaSnapshot unlimited() {
            return new QuotaSnapshot(false, false, LocalDate.now(ZoneOffset.UTC), "PRO", 0, 0, OffsetDateTime.now(ZoneOffset.UTC), null);
        }

        static QuotaSnapshot blocked(LocalDate day, String tier, long dailyLimit, long usedTokens, OffsetDateTime resetAt) {
            return new QuotaSnapshot(true, true, day, tier, dailyLimit, usedTokens, resetAt, 0);
        }

        static QuotaSnapshot allowed(LocalDate day, String tier, long dailyLimit, long usedTokens, OffsetDateTime resetAt, int maxCompletionTokens) {
            return new QuotaSnapshot(true, false, day, tier, dailyLimit, usedTokens, resetAt, maxCompletionTokens);
        }

        QuotaSnapshot withUsedTokens(long usedTokens) {
            return new QuotaSnapshot(trackTokens, blocked, day, tier, dailyLimit, usedTokens, resetAt, maxCompletionTokensForThisRequest);
        }

        Map<String, Object> toFrontendPayload() {
            long remaining = dailyLimit > 0 ? Math.max(0, dailyLimit - usedTokens) : -1;
            return Map.of(
                    "tier", tier,
                    "day", day.toString(),
                    "dailyLimit", dailyLimit,
                    "usedTokens", usedTokens,
                    "remainingTokens", remaining,
                    "resetAt", resetAt != null ? resetAt.toString() : null,
                    "provider", "local-vllm"
            );
        }
    }
}
