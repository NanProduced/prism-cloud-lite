package nan.produced.prism.core.assistant.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient;
import nan.produced.prism.core.assistant.infrastructure.llm.RawAssistantChatLlmClient;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelRouter;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantSpringAiToolCallbacks;
import nan.produced.prism.core.assistant.infrastructure.springai.SpringAiAssistantChatLlmClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AssistantChatProperties.class)
public class AssistantChatConfiguration {

    @Bean
    public RestClient assistantLlmRestClient(AssistantChatProperties properties, RestClient.Builder builder) {
        return builder
                .baseUrl(properties.llm().baseUrl())
                .build();
    }

    @Bean
    public OpenAiChatCompletionsClient openAiChatCompletionsClient(AssistantChatProperties properties, RestClient assistantLlmRestClient) {
        return new OpenAiChatCompletionsClient(assistantLlmRestClient, properties.llm().model(), properties.llm().apiKey(), properties.llm().temperature());
    }

    @Bean
    public AssistantChatLlmClient assistantChatLlmClient(AssistantChatProperties properties,
                                                         OpenAiChatCompletionsClient openAiChatCompletionsClient,
                                                         AssistantChatModelRouter router,
                                                         AssistantSpringAiToolCallbacks assistantSpringAiToolCallbacks,
                                                         AssistantToolExecutor assistantToolExecutor,
                                                         ObjectMapper objectMapper,
                                                         ToolCallingManager assistantToolCallingManager,
                                                         RetryTemplate assistantRetryTemplate,
                                                         ObservationRegistry observationRegistry) {
        if ("spring-ai".equalsIgnoreCase(properties.engine())) {
            return new SpringAiAssistantChatLlmClient(
                    properties,
                    router,
                    assistantSpringAiToolCallbacks,
                    assistantToolExecutor,
                    objectMapper,
                    assistantToolCallingManager,
                    assistantRetryTemplate,
                    observationRegistry
            );
        }
        return new RawAssistantChatLlmClient(openAiChatCompletionsClient);
    }
}
