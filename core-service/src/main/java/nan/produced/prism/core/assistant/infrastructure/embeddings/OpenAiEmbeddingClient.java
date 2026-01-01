package nan.produced.prism.core.assistant.infrastructure.embeddings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OpenAiEmbeddingClient {

    private final RestClient restClient;
    private final String model;
    private final int dimension;

    public OpenAiEmbeddingClient(RestClient restClient, String model, int dimension) {
        this.restClient = restClient;
        this.model = model;
        this.dimension = dimension;
    }

    public List<float[]> embedAll(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        var request = new EmbeddingsRequest(model, inputs);
        EmbeddingsResponse response = restClient.post()
                .uri("/embeddings")
                .body(request)
                .retrieve()
                .body(EmbeddingsResponse.class);

        if (response == null || response.data == null || response.data.isEmpty()) {
            throw new IllegalStateException("Empty embeddings response");
        }

        List<float[]> embeddings = new ArrayList<>(response.data.size());
        for (var item : response.data) {
            if (item == null || item.embedding == null) {
                throw new IllegalStateException("Embedding item missing embedding");
            }
            if (item.embedding.size() != dimension) {
                throw new IllegalStateException("Unexpected embedding dimension: expected=%s actual=%s"
                        .formatted(dimension, item.embedding.size()));
            }
            float[] vector = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vector[i] = Objects.requireNonNull(item.embedding.get(i)).floatValue();
            }
            embeddings.add(vector);
        }
        return embeddings;
    }

    public record EmbeddingsRequest(
            String model,
            Object input
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingsResponse {
        public List<EmbeddingData> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {
        @JsonProperty("embedding")
        public List<Double> embedding;
    }
}

