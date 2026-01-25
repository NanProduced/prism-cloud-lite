package nan.produced.prism.core.assistant.application.chat;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record AssistantChatTierLimits(
        // 本次请求送进模型的“历史消息”最多保留多少条；从最后一条 user 往前截断
        int historyMaxMessages,
        // 保留的历史消息内容总字符数上限（按 content().length() 累加）；超出就丢更早的消息，但会尽量保住最后一条 user
        int historyMaxChars,
        // 该档位是否允许启用 RAG；实际生效还要同时满足全局开关 assistant.chat.rag.enabled
        boolean ragEnabled,
        // RAG检索时取相似度最高的K个 chunk
        int ragTopK,
        // 拼进系统 Prompt 的 RAG context 最大字符数（会截断到该长度）
        int ragMaxContextChars,
        // 每一轮里最多允许执行多少个 tool call；超过会停止继续执行并返回 toolLimit 错误
        int toolMaxCallsPerRound,
        // 工具执行结果（JSON）回填给模型/ToolResponse 时的最大字符数，用于截断防止 prompt 过大
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
                5,
                6_000
        );
    }

    public static AssistantChatTierLimits freeByokDefaults() {
        // User uses their own API key, so we can be more permissive (still keep a hard cap).
        return proDefaults();
    }
}
