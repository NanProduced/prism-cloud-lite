package nan.produced.prism.core.assistant.infrastructure.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantRagProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantRerankClient {

    private final AssistantRagProperties properties;
    private final RestClient.Builder restClientBuilder;

    public record RerankResult(int index, Double relevanceScore) {
    }

    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (!StringUtils.hasText(query) || documents == null || documents.isEmpty() || topN <= 0) {
            return List.of();
        }
        if (properties.search() == null || properties.search().rerank() == null || !properties.search().rerank().enabled()) {
            return List.of();
        }
        String baseUrl = properties.search().rerank().baseUrl();
        String model = properties.search().rerank().model();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            return List.of();
        }

        try {
            RestClient restClient = restClientBuilder.baseUrl(baseUrl).build();
            RerankRequest request = new RerankRequest(model, query, documents, topN);
            RerankResponse response = restClient.post()
                    .uri("/v1/rerank")
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);
            if (response == null || response.results() == null || response.results().isEmpty()) {
                return List.of();
            }

            List<RerankResult> results = new ArrayList<>(response.results().size());
            for (RerankResponse.Result result : response.results()) {
                results.add(new RerankResult(result.index(), result.relevanceScore()));
            }
            results.sort(Comparator.comparing(AssistantRerankClient.RerankResult::relevanceScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return results;
        } catch (Exception e) {
            log.warn("[assistant.rag.rerank] failed to rerank: {}", e.getMessage());
            return List.of();
        }
    }

    public record RerankRequest(
            String model,
            String query,
            List<String> documents,
            @JsonProperty("top_n") int topN
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(List<Result> results) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Result(
                int index,
                @JsonProperty("relevance_score") Double relevanceScore
        ) {
        }
    }
}
