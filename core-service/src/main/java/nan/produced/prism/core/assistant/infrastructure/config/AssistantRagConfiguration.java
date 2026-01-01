package nan.produced.prism.core.assistant.infrastructure.config;

import nan.produced.prism.core.assistant.infrastructure.embeddings.OpenAiEmbeddingClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AssistantRagProperties.class)
public class AssistantRagConfiguration {

    @Bean
    public RestClient assistantEmbeddingsRestClient(AssistantRagProperties properties, RestClient.Builder builder) {
        return builder
                .baseUrl(properties.embedding().baseUrl())
                .build();
    }

    @Bean
    public OpenAiEmbeddingClient openAiEmbeddingClient(AssistantRagProperties properties, RestClient assistantEmbeddingsRestClient) {
        return new OpenAiEmbeddingClient(assistantEmbeddingsRestClient, properties.embedding().model(), properties.embedding().dimension());
    }
}

