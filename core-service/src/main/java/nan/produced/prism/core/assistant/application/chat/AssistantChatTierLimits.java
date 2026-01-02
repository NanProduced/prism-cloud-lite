package nan.produced.prism.core.assistant.application.chat;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record AssistantChatTierLimits(
        int historyMaxMessages,
        int historyMaxChars,
        boolean ragEnabled,
        int ragTopK,
        int ragMaxContextChars,
        int toolMaxRounds,
        int toolMaxCallsPerRound,
        int toolResultMaxChars
) {

    public static final String TIER_FREE = "FREE";
    public static final String TIER_PRO = "PRO";
    public static final String TIER_FREE_BYOK = "FREE_BYOK";

    public static String normalizeTierKey(String tier) {
        if (!StringUtils.hasText(tier)) {
            return TIER_FREE;
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        if (TIER_PRO.equals(upper)) {
            return TIER_PRO;
        }
        if (TIER_FREE_BYOK.equals(upper)) {
            return TIER_FREE_BYOK;
        }
        return TIER_FREE;
    }

    public static AssistantChatTierLimits freeDefaults() {
        return new AssistantChatTierLimits(
                10,
                6_000,
                true,
                4,
                5_000,
                1,
                2,
                1_500
        );
    }

    public static AssistantChatTierLimits proDefaults() {
        return new AssistantChatTierLimits(
                30,
                16_000,
                true,
                8,
                12_000,
                3,
                5,
                6_000
        );
    }

    public static AssistantChatTierLimits freeByokDefaults() {
        // User uses their own API key, so we can be more permissive (still keep a hard cap).
        return proDefaults();
    }
}

