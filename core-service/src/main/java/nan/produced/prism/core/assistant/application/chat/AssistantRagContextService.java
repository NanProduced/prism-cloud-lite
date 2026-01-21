package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.rag.RagMetadataKeys;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantRagParentRepository;
import nan.produced.prism.core.assistant.infrastructure.rag.AssistantRerankClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssistantRagContextService {

    private final AssistantChatProperties chatProperties;
    private final AssistantRagProperties ragProperties;
    private final VectorStore vectorStore;
    private final AssistantRagParentRepository parentRepository;
    private final AssistantRerankClient rerankClient;

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

        List<ParentCandidate> candidates = retrieveCandidates(userText, preferLang, effectiveTopK);
        if (candidates.isEmpty()) {
            String fallback = "zh".equals(preferLang) ? "en" : "zh";
            candidates = retrieveCandidates(userText, fallback, effectiveTopK);
        }
        if (candidates.isEmpty()) {
            return RagContext.empty();
        }

        List<ParentCandidate> ranked = applyRerank(userText, candidates, effectiveTopK);
        return buildContextText(ranked, effectiveMaxChars);
    }

    private List<ParentCandidate> retrieveCandidates(String query, String lang, int topK) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(lang)) {
            return List.of();
        }

        int vectorTopK = resolveTopK(ragProperties.search().vectorTopK(), topK, 3);
        int ftsTopK = resolveTopK(ragProperties.search().ftsTopK(), topK, 3);
        int hybridTopN = resolveHybridTopN(topK, vectorTopK, ftsTopK, ragProperties.search().hybridTopN());

        Map<UUID, Candidate> merged = new LinkedHashMap<>();
        List<Document> vectorHits = vectorTopK > 0 ? vectorStore.similaritySearch(buildVectorSearch(query, lang, vectorTopK)) : List.of();
        if (vectorHits != null && !vectorHits.isEmpty()) {
            Map<UUID, AssistantRagParentRepository.ParentDoc> parentsById = loadParents(vectorHits);
            int rank = 1;
            for (Document doc : vectorHits) {
                UUID parentId = parseParentId(doc);
                if (parentId == null) {
                    rank++;
                    continue;
                }
                AssistantRagParentRepository.ParentDoc parent = parentsById.get(parentId);
                if (parent == null) {
                    rank++;
                    continue;
                }
                Candidate candidate = merged.computeIfAbsent(parentId, id -> new Candidate(parent));
                if (candidate.vectorRank == null) {
                    candidate.vectorRank = rank;
                }
                rank++;
            }
        }

        if (ftsTopK > 0) {
            List<AssistantRagParentRepository.ParentHit> ftsHits = parentRepository.searchByFullText(
                    query,
                    lang,
                    ragProperties.ingest().requireAudience(),
                    ragProperties.ingest().requireStatus(),
                    ftsTopK
            );
            int rank = 1;
            for (AssistantRagParentRepository.ParentHit hit : ftsHits) {
                AssistantRagParentRepository.ParentDoc parent = new AssistantRagParentRepository.ParentDoc(
                        hit.id(),
                        hit.docKey(),
                        hit.lang(),
                        hit.slug(),
                        hit.title(),
                        hit.headingPath(),
                        hit.parentIndex(),
                        hit.parentText()
                );
                Candidate candidate = merged.computeIfAbsent(hit.id(), id -> new Candidate(parent));
                if (candidate.ftsRank == null) {
                    candidate.ftsRank = rank;
                }
                rank++;
            }
        }

        if (merged.isEmpty()) {
            return List.of();
        }

        int rrfK = ragProperties.search().rrfK();
        List<ParentCandidate> ranked = merged.values().stream()
                .map(candidate -> candidate.toRanked(rrfK))
                .sorted(Comparator.comparing(ParentCandidate::rrfScore).reversed()
                        .thenComparing(ParentCandidate::bestRank))
                .limit(hybridTopN)
                .toList();

        return ranked;
    }

    private SearchRequest buildVectorSearch(String query, String lang, int topK) {
        String filterExpression = buildVectorFilter(lang);
        return SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();
    }

    private String buildVectorFilter(String lang) {
        String audience = ragProperties.ingest().requireAudience();
        String status = ragProperties.ingest().requireStatus();
        StringBuilder sb = new StringBuilder("WHERE ");
        sb.append(RagMetadataKeys.LANG).append(" == '").append(lang).append("'");
        if (StringUtils.hasText(audience)) {
            sb.append(" AND ").append(RagMetadataKeys.AUDIENCE).append(" == '").append(audience).append("'");
        }
        if (StringUtils.hasText(status)) {
            sb.append(" AND ").append(RagMetadataKeys.STATUS).append(" == '").append(status).append("'");
        }
        return sb.toString();
    }

    private Map<UUID, AssistantRagParentRepository.ParentDoc> loadParents(List<Document> vectorHits) {
        List<UUID> ids = new ArrayList<>();
        for (Document doc : vectorHits) {
            UUID parentId = parseParentId(doc);
            if (parentId != null) {
                ids.add(parentId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AssistantRagParentRepository.ParentDoc> map = new LinkedHashMap<>();
        for (AssistantRagParentRepository.ParentDoc parent : parentRepository.findByIds(ids)) {
            map.put(parent.id(), parent);
        }
        return map;
    }

    private List<ParentCandidate> applyRerank(String query, List<ParentCandidate> candidates, int topK) {
        if (candidates.size() <= 1 || !ragProperties.search().rerank().enabled()) {
            return candidates.stream().limit(topK).toList();
        }

        int rerankTopN = Math.min(Math.max(ragProperties.search().rerank().topN(), topK), candidates.size());
        List<ParentCandidate> subset = candidates.subList(0, rerankTopN);
        List<String> rerankDocs = subset.stream().map(ParentCandidate::rerankText).toList();
        List<AssistantRerankClient.RerankResult> reranked = rerankClient.rerank(query, rerankDocs, rerankTopN);
        if (reranked.isEmpty()) {
            return candidates.stream().limit(topK).toList();
        }

        List<ParentCandidate> ordered = new ArrayList<>();
        boolean[] used = new boolean[subset.size()];
        for (AssistantRerankClient.RerankResult result : reranked) {
            int idx = result.index();
            if (idx >= 0 && idx < subset.size() && !used[idx]) {
                ordered.add(subset.get(idx));
                used[idx] = true;
            }
        }
        for (int i = 0; i < subset.size(); i++) {
            if (!used[i]) {
                ordered.add(subset.get(i));
            }
        }
        return ordered.stream().limit(topK).toList();
    }

    private RagContext buildContextText(List<ParentCandidate> candidates, int maxContextChars) {
        Map<String, RagSource> sourcesBySlug = new LinkedHashMap<>();
        StringBuilder context = new StringBuilder(Math.min(maxContextChars, 16_000));

        for (ParentCandidate candidate : candidates) {
            AssistantRagParentRepository.ParentDoc parent = candidate.parent();
            if (parent == null || !StringUtils.hasText(parent.parentText())) {
                continue;
            }

            sourcesBySlug.putIfAbsent(parent.slug(), new RagSource(
                    parent.docKey() + "/" + parent.lang(),
                    parent.slug(),
                    parent.title()
            ));

            if (context.length() >= maxContextChars) {
                break;
            }

            context.append("\n---\n");
            context.append("Source: ").append(parent.title()).append(" (").append(parent.lang()).append(") ").append(parent.slug()).append("\n");
            if (StringUtils.hasText(parent.headingPath())) {
                context.append("Heading: ").append(parent.headingPath()).append("\n");
            }
            context.append(parent.parentText()).append("\n");
        }

        String text = context.toString().trim();
        if (text.length() > maxContextChars) {
            text = text.substring(0, maxContextChars);
        }
        return new RagContext(text, List.copyOf(sourcesBySlug.values()));
    }

    private static int resolveTopK(int configured, int base, int multiplier) {
        if (configured > 0) {
            return configured;
        }
        int fallback = base * multiplier;
        return Math.max(base, fallback);
    }

    private static int resolveHybridTopN(int base, int vectorTopK, int ftsTopK, int configured) {
        if (configured > 0) {
            return configured;
        }
        return Math.max(base, Math.max(vectorTopK, ftsTopK));
    }

    private static UUID parseParentId(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return null;
        }
        Object raw = doc.getMetadata().get(RagMetadataKeys.PARENT_ID);
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                return UUID.fromString(s.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
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

    private static final class Candidate {
        private final AssistantRagParentRepository.ParentDoc parent;
        private Integer vectorRank;
        private Integer ftsRank;

        private Candidate(AssistantRagParentRepository.ParentDoc parent) {
            this.parent = parent;
        }

        private ParentCandidate toRanked(int rrfK) {
            double score = 0.0;
            if (vectorRank != null) {
                score += 1.0d / (rrfK + vectorRank);
            }
            if (ftsRank != null) {
                score += 1.0d / (rrfK + ftsRank);
            }
            int bestRank = Integer.MAX_VALUE;
            if (vectorRank != null) {
                bestRank = Math.min(bestRank, vectorRank);
            }
            if (ftsRank != null) {
                bestRank = Math.min(bestRank, ftsRank);
            }
            return new ParentCandidate(parent, vectorRank, ftsRank, score, bestRank);
        }
    }

    private record ParentCandidate(AssistantRagParentRepository.ParentDoc parent,
                                   Integer vectorRank,
                                   Integer ftsRank,
                                   double rrfScore,
                                   int bestRank) {
        private String rerankText() {
            if (parent == null) {
                return "";
            }
            String heading = parent.headingPath();
            if (StringUtils.hasText(heading)) {
                return heading + "\n\n" + Objects.toString(parent.parentText(), "");
            }
            return Objects.toString(parent.parentText(), "");
        }
    }
}
