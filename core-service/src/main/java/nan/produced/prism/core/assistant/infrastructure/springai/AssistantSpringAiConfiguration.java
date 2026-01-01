package nan.produced.prism.core.assistant.infrastructure.springai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.util.json.schema.SchemaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class AssistantSpringAiConfiguration {

    @Bean
    public ToolCallbackResolver assistantToolCallbackResolver(GenericApplicationContext applicationContext) {
        return new SpringBeanToolCallbackResolver(applicationContext, SchemaType.JSON_SCHEMA);
    }

    @Bean
    public ToolExecutionExceptionProcessor assistantToolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }

    @Bean
    public ToolCallingManager assistantToolCallingManager(ObservationRegistry observationRegistry,
                                                          ToolCallbackResolver assistantToolCallbackResolver,
                                                          ToolExecutionExceptionProcessor assistantToolExecutionExceptionProcessor) {
        return new DefaultToolCallingManager(observationRegistry, assistantToolCallbackResolver, assistantToolExecutionExceptionProcessor);
    }

    @Bean
    public RetryTemplate assistantRetryTemplate() {
        return RetryUtils.DEFAULT_RETRY_TEMPLATE;
    }
}

