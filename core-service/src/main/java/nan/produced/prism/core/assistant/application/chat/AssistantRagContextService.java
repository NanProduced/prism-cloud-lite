package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.ingest.VectorLiterals;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.embeddings.OpenAiEmbeddingClient;
import nan.produced.prism.core.assistant.infrastructure.persistence.RagDocsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssistantRagContextService {

    private final AssistantChatProperties properties;
    private final OpenAiEmbeddingClient embeddingClient;
    private final RagDocsRepository ragDocsRepository;

    public record RagSource(String sourceId, String url, String title) {
    }

    public record RagContext(String contextText, List<RagSource> sources) {
        public static RagContext empty() {
            return new RagContext("", List.of());
        }
    }

    public RagContext buildContext(String userText) {
        String preferLang = normalizePreferLang(properties.rag().preferLang(), userText);

        float[] embedding = embeddingClient.embedAll(List.of(userText)).get(0);
        String queryVector = VectorLiterals.toPgVectorLiteral(embedding);

        List<RagDocsRepository.RagChunkHit> hits = new ArrayList<>();
        hits.addAll(ragDocsRepository.searchTopChunks(queryVector, preferLang, properties.rag().topK()));
        if (hits.isEmpty()) {
            String fallback = "zh".equals(preferLang) ? "en" : "zh";
            hits.addAll(ragDocsRepository.searchTopChunks(queryVector, fallback, properties.rag().topK()));
        }

        if (hits.isEmpty()) {
            return RagContext.empty();
        }

        // Keep sources stable + deduplicated by doc slug.
        Map<String, RagSource> sourcesBySlug = new LinkedHashMap<>();
        StringBuilder context = new StringBuilder(Math.min(properties.rag().maxContextChars(), 16_000));

        for (RagDocsRepository.RagChunkHit hit : hits) {
            String slug = hit.slug();
            sourcesBySlug.putIfAbsent(slug, new RagSource(
                    hit.docKey() + "/" + hit.lang(),
                    slug,
                    hit.title()
            ));

            if (context.length() >= properties.rag().maxContextChars()) {
                break;
            }

            context.append("\n---\n");
            context.append("Source: ").append(hit.title()).append(" (").append(hit.lang()).append(") ").append(slug).append("\n");
            if (hit.headingPath() != null && !hit.headingPath().isBlank()) {
                context.append("Heading: ").append(hit.headingPath()).append("\n");
            }
            context.append(hit.chunkText()).append("\n");
        }

        return new RagContext(context.toString().trim(), List.copyOf(sourcesBySlug.values()));
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

