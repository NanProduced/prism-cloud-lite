package nan.produced.prism.core.assistant.infrastructure.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(AssistantRagProperties.class)
public class AssistantRagConfiguration {

    @Bean
    public OpenAiApi assistantRagOpenAiApi(AssistantRagProperties properties) {
        String apiKey = StringUtils.hasText(properties.embedding().apiKey())
                ? properties.embedding().apiKey()
                : "local";
        return OpenAiApi.builder()
                .baseUrl(properties.embedding().baseUrl())
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public EmbeddingModel assistantRagEmbeddingModel(OpenAiApi assistantRagOpenAiApi,
                                                     AssistantRagProperties properties) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.embedding().model())
                .dimensions(properties.embedding().dimension())
                .build();
        return new OpenAiEmbeddingModel(assistantRagOpenAiApi, MetadataMode.NONE, options);
    }

    @Bean
    public PgVectorStore assistantRagVectorStore(JdbcTemplate jdbcTemplate,
                                                 EmbeddingModel assistantRagEmbeddingModel,
                                                 AssistantRagProperties properties) {
        return PgVectorStore.builder(jdbcTemplate, assistantRagEmbeddingModel)
                .schemaName("assistant")
                .vectorTableName("rag_vector")
                .idType(PgVectorStore.PgIdType.UUID)
                .dimensions(properties.embedding().dimension())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .maxDocumentBatchSize(properties.embedding().batchSize())
                .build();
    }
}
