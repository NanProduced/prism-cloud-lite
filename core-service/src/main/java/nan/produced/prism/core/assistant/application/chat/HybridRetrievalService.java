package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.rag.RagMetadataKeys;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantRagParentRepository;
import nan.produced.prism.core.assistant.infrastructure.rag.AssistantRerankClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final AssistantRagProperties ragProperties;
    private final VectorStore vectorStore;
    private final AssistantRagParentRepository parentRepository;
    private final AssistantRerankClient rerankClient;

    public List<AssistantRagParentRepository.ParentDoc> retrieve(String query, String lang, int topK) {
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

        List<ParentCandidate> reranked = applyRerank(query, ranked, topK);
        return reranked.stream()
                .map(ParentCandidate::parent)
                .filter(Objects::nonNull)
                .toList();
    }

    private SearchRequest buildVectorSearch(String query, String lang, int topK) {
        Filter.Expression filterExpression = buildVectorFilterExpression(lang);
        return SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();
    }

    private Filter.Expression buildVectorFilterExpression(String lang) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op exp = builder.eq(RagMetadataKeys.LANG, lang);

        String audience = ragProperties.ingest().requireAudience();
        if (StringUtils.hasText(audience)) {
            exp = builder.and(exp, builder.eq(RagMetadataKeys.AUDIENCE, audience));
        }

        String status = ragProperties.ingest().requireStatus();
        if (StringUtils.hasText(status)) {
            exp = builder.and(exp, builder.eq(RagMetadataKeys.STATUS, status));
        }

        return exp.build();
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
