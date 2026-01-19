package nan.produced.prism.core.common.messaging;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

@Configuration
public class RabbitListenerConfiguration {

    // Decision record:
    // - device events -> RejectAndDontRequeueRecoverer + DLQ
    // - task queues -> RepublishMessageRecoverer to error exchange

    @Value("${prism.rabbitmq.listener.device.prefetch:50}")
    private int devicePrefetch;

    @Value("${prism.rabbitmq.listener.device.concurrency:2}")
    private int deviceConcurrency;

    @Value("${prism.rabbitmq.listener.device.max-concurrency:6}")
    private int deviceMaxConcurrency;

    @Value("${prism.rabbitmq.listener.device.retry.max-attempts:3}")
    private int deviceRetryMaxAttempts;

    @Value("${prism.rabbitmq.listener.device.retry.initial-interval:500}")
    private long deviceRetryInitialInterval;

    @Value("${prism.rabbitmq.listener.device.retry.multiplier:2.0}")
    private double deviceRetryMultiplier;

    @Value("${prism.rabbitmq.listener.device.retry.max-interval:5000}")
    private long deviceRetryMaxInterval;

    @Value("${prism.rabbitmq.listener.task.prefetch:10}")
    private int taskPrefetch;

    @Value("${prism.rabbitmq.listener.task.retry.max-attempts:5}")
    private int taskRetryMaxAttempts;

    @Value("${prism.rabbitmq.listener.task.retry.initial-interval:1000}")
    private long taskRetryInitialInterval;

    @Value("${prism.rabbitmq.listener.task.retry.multiplier:2.0}")
    private double taskRetryMultiplier;

    @Value("${prism.rabbitmq.listener.task.retry.max-interval:10000}")
    private long taskRetryMaxInterval;

    @Bean
    public MethodInterceptor deviceEventRetryInterceptor() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                deviceRetryMaxAttempts,
                Map.of(AmqpRejectAndDontRequeueException.class, false),
                true,
                true
        );
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(deviceRetryInitialInterval, deviceRetryMultiplier, deviceRetryMaxInterval)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public RepublishMessageRecoverer taskRepublishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        // Use core notifications error exchange for task failures.
        return new RepublishMessageRecoverer(
                rabbitTemplate,
                MessagingConstants.Exchanges.CORE_NOTIFICATIONS_ERROR,
                MessagingConstants.RoutingKeys.TASK_ERROR
        );
    }

    @Bean
    public MethodInterceptor taskRetryInterceptor(RepublishMessageRecoverer taskRepublishMessageRecoverer) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(taskRetryMaxAttempts)
                .backOffOptions(taskRetryInitialInterval, taskRetryMultiplier, taskRetryMaxInterval)
                .recoverer(taskRepublishMessageRecoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory deviceEventRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MethodInterceptor deviceEventRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(devicePrefetch);
        factory.setConcurrentConsumers(deviceConcurrency);
        factory.setMaxConcurrentConsumers(deviceMaxConcurrency);
        factory.setAdviceChain(deviceEventRetryInterceptor);
        return factory;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory taskRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MethodInterceptor taskRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(taskPrefetch);
        factory.setAdviceChain(taskRetryInterceptor);
        return factory;
    }
}
