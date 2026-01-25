package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatTierLimitsProperties;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelRouter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistantChatRuntimePolicyService {

    private final AssistantChatTierLimitsProperties tierLimits;
    private final AssistantChatModelRouter modelRouter;

    public AssistantChatTierLimits resolveLimits(UUID userId, String tier) {
        String tierKey = AssistantChatTierLimits.normalizeTierKey(tier);
        if (modelRouter != null) {
            try {
                AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
                boolean byok = target != null
                        && StringUtils.hasText(target.provider())
                        && !"local-vllm".equalsIgnoreCase(target.provider())
                        && StringUtils.hasText(target.apiKey());
                if (byok && AssistantChatTierLimits.TIER_FREE.equalsIgnoreCase(tierKey)) {
                    tierKey = AssistantChatTierLimits.TIER_FREE_BYOK;
                }
            } catch (Exception ignored) {
            }
        }
        return tierLimits.getOrDefault(tierKey);
    }

    public boolean isLocalVllmCall(UUID userId) {
        if (modelRouter == null) {
            return true;
        }
        try {
            AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
            return target == null || "local-vllm".equalsIgnoreCase(target.provider());
        } catch (Exception e) {
            return true;
        }
    }
}
