package nan.produced.prism.core.assistant.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import nan.produced.prism.core.assistant.application.chat.AssistantChatTierLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "assistant.chat.limits")
public class AssistantChatTierLimitsProperties {

    /**
     * Tier keys: FREE / PRO / FREE_BYOK.
     */
    private Map<String, AssistantChatTierLimits> byTier = defaultLimits();

    public AssistantChatTierLimits getOrDefault(String tierKey) {
        String normalized = AssistantChatTierLimits.normalizeTierKey(tierKey);
        AssistantChatTierLimits limits = byTier != null ? byTier.get(normalized) : null;
        if (limits == null && byTier != null) {
            for (var e : byTier.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(normalized)) {
                    limits = e.getValue();
                    break;
                }
            }
        }
        if (limits != null) {
            return limits;
        }
        return switch (normalized) {
            case AssistantChatTierLimits.TIER_PRO -> AssistantChatTierLimits.proDefaults();
            case AssistantChatTierLimits.TIER_FREE_BYOK -> AssistantChatTierLimits.freeByokDefaults();
            default -> AssistantChatTierLimits.freeDefaults();
        };
    }

    private static Map<String, AssistantChatTierLimits> defaultLimits() {
        Map<String, AssistantChatTierLimits> map = new HashMap<>();
        map.put(AssistantChatTierLimits.TIER_FREE, AssistantChatTierLimits.freeDefaults());
        map.put(AssistantChatTierLimits.TIER_PRO, AssistantChatTierLimits.proDefaults());
        map.put(AssistantChatTierLimits.TIER_FREE_BYOK, AssistantChatTierLimits.freeByokDefaults());
        return map;
    }
}

