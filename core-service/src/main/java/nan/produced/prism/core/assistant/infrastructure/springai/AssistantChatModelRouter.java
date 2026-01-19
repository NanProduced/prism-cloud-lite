package nan.produced.prism.core.assistant.infrastructure.springai;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.credentials.UserAiModelConfigService;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * LLM 模型路由
 * <p>根据不同用户的配置或权限，动态决定使用哪个大模型（LLM）后端</p>
 */
@Component
@RequiredArgsConstructor
public class AssistantChatModelRouter {

    private final AssistantChatProperties properties;
    private final UserAiModelConfigService userAiModelConfigService;

    public record LlmTarget(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            double temperature
    ) {
    }

    public LlmTarget resolveForUser(UUID userId) {
        UserAiModelConfigService.ResolvedDefaultModel resolved = userAiModelConfigService.resolveDefaultModelForUser(userId);
        if (resolved != null) {
            LlmTarget target = resolveByProvider(resolved.provider(), resolved.model(), resolved.apiKey());
            if (target != null) {
                return target;
            }
        }
        return resolveLocalFallback();
    }

    private LlmTarget resolveByProvider(String provider, String model, String apiKey) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String p = provider.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "local-vllm" -> new LlmTarget(
                    "local-vllm",
                    properties.llm().baseUrl(),
                    StringUtils.hasText(model) ? model : properties.llm().model(),
                    StringUtils.hasText(apiKey) ? apiKey : properties.llm().apiKey(),
                    properties.llm().temperature()
            );
            case "openai" -> {
                if (!StringUtils.hasText(apiKey)) {
                    yield null;
                }
                AssistantChatProperties.Provider openai = properties.providers() != null ? properties.providers().openai() : null;
                String baseUrl = openai != null ? openai.baseUrl() : null;
                String defaultModel = openai != null ? openai.defaultModel() : null;
                if (!StringUtils.hasText(baseUrl)) {
                    yield null;
                }
                yield new LlmTarget(
                        "openai",
                        baseUrl,
                        StringUtils.hasText(model) ? model : defaultModel,
                        apiKey,
                        properties.llm().temperature()
                );
            }
            case "gemini" -> {
                if (!StringUtils.hasText(apiKey)) {
                    yield null;
                }
                AssistantChatProperties.Provider gemini = properties.providers() != null ? properties.providers().gemini() : null;
                String baseUrl = gemini != null ? gemini.baseUrl() : null;
                String defaultModel = gemini != null ? gemini.defaultModel() : null;
                if (!StringUtils.hasText(baseUrl)) {
                    yield null;
                }
                yield new LlmTarget(
                        "gemini",
                        baseUrl,
                        StringUtils.hasText(model) ? model : defaultModel,
                        apiKey,
                        properties.llm().temperature()
                );
            }
            default -> null;
        };
    }

    private LlmTarget resolveLocalFallback() {
        return new LlmTarget(
                "local-vllm",
                properties.llm().baseUrl(),
                properties.llm().model(),
                properties.llm().apiKey(),
                properties.llm().temperature()
        );
    }
}

