package nan.produced.prism.core.assistant.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelFactory;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelRouter;
import nan.produced.prism.core.assistant.infrastructure.springai.SpringAiAssistantChatLlmClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AssistantChatProperties.class, AssistantChatTierLimitsProperties.class, AssistantChatTokenBudgetProperties.class})
public class AssistantChatConfiguration {

    @Bean
    public AssistantChatLlmClient assistantChatLlmClient(AssistantChatModelRouter router,
                                                         AssistantChatModelFactory assistantChatModelFactory,
                                                         ObjectMapper objectMapper) {
        return new SpringAiAssistantChatLlmClient(
                router,
                assistantChatModelFactory,
                objectMapper
        );
    }
}
