package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantRagParentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AssistantRagContextService {

    private final AssistantChatProperties chatProperties;
    private final HybridRetrievalService retrievalService;
    private final RagContextRenderer contextRenderer;

    public record RagSource(String sourceId, String url, String title) {
    }

    public record RagContext(String contextText, List<RagSource> sources) {
        public static RagContext empty() {
            return new RagContext("", List.of());
        }
    }

    public RagContext buildContext(String userText) {
        return buildContext(userText, chatProperties.rag().topK(), chatProperties.rag().maxContextChars(), chatProperties.rag().preferLang());
    }

    public RagContext buildContext(String userText, int topK, int maxContextChars, String preferLangSetting) {
        int effectiveTopK = Math.max(1, topK);
        int effectiveMaxChars = Math.max(1, maxContextChars);
        String preferLang = normalizePreferLang(preferLangSetting, userText);

        List<AssistantRagParentRepository.ParentDoc> parents =
                retrievalService.retrieve(userText, preferLang, effectiveTopK);
        if (parents.isEmpty()) {
            String fallback = "zh".equals(preferLang) ? "en" : "zh";
            parents = retrievalService.retrieve(userText, fallback, effectiveTopK);
        }
        if (parents.isEmpty()) {
            return RagContext.empty();
        }

        return contextRenderer.render(parents, effectiveMaxChars);
    }

    private static String normalizePreferLang(String preferLang, String userText) {
        String trimmed = preferLang == null ? "" : preferLang.trim().toLowerCase(Locale.ROOT);
        if ("zh".equals(trimmed) || "en".equals(trimmed)) {
            return trimmed;
        }
        return containsCjk(userText) ? "zh" : "en";
    }

    private static boolean containsCjk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }
}
