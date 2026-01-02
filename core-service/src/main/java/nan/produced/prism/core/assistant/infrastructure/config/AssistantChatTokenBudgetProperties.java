package nan.produced.prism.core.assistant.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "assistant.chat.token-budget")
public class AssistantChatTokenBudgetProperties {

    /**
     * When enabled, apply daily token budgets for local-vllm only.
     */
    private boolean enabled = true;

    /**
     * Zone for "daily" reset boundary.
     */
    private String resetZone = "UTC";

    /**
     * Daily total token limit (prompt+completion). 0 or negative means unlimited.
     * Keys: FREE/PRO
     */
    private Map<String, Long> dailyLimitByTier = defaultDailyLimit();

    /**
     * Per-request completion token cap. 0 or negative means no cap.
     * Keys: FREE/PRO
     */
    private Map<String, Integer> maxCompletionTokensByTier = defaultMaxCompletionTokens();

    public ZoneId resetZoneId() {
        try {
            return ZoneId.of(StringUtils.hasText(resetZone) ? resetZone.trim() : "UTC");
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    public long getDailyLimitOrUnlimited(String tier) {
        String key = normalizeTier(tier);
        Long v = dailyLimitByTier != null ? dailyLimitByTier.get(key) : null;
        if (v == null && dailyLimitByTier != null) {
            for (var e : dailyLimitByTier.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    v = e.getValue();
                    break;
                }
            }
        }
        return v != null ? v : ("PRO".equals(key) ? 0 : 20_000L);
    }

    public int getMaxCompletionTokensOrDefault(String tier) {
        String key = normalizeTier(tier);
        Integer v = maxCompletionTokensByTier != null ? maxCompletionTokensByTier.get(key) : null;
        if (v == null && maxCompletionTokensByTier != null) {
            for (var e : maxCompletionTokensByTier.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    v = e.getValue();
                    break;
                }
            }
        }
        return v != null ? v : ("PRO".equals(key) ? 4096 : 512);
    }

    private static String normalizeTier(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        return "PRO".equals(upper) ? "PRO" : "FREE";
    }

    private static Map<String, Long> defaultDailyLimit() {
        Map<String, Long> m = new HashMap<>();
        m.put("FREE", 20_000L);
        m.put("PRO", 0L);
        return m;
    }

    private static Map<String, Integer> defaultMaxCompletionTokens() {
        Map<String, Integer> m = new HashMap<>();
        m.put("FREE", 512);
        m.put("PRO", 4096);
        return m;
    }
}

