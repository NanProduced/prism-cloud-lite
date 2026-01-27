package nan.produced.prism.gateway.realtime.messaging;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitListenerConfiguration {

    // Gateway only consumes notifications; use RejectAndDontRequeueRecoverer + DLQ.

    @Value("${prism.rabbitmq.listener.gateway.prefetch:100}")
    private int prefetch;

    @Value("${prism.rabbitmq.listener.gateway.concurrency:2}")
    private int concurrency;

    @Value("${prism.rabbitmq.listener.gateway.max-concurrency:6}")
    private int maxConcurrency;

    @Value("${prism.rabbitmq.listener.gateway.retry.max-attempts:2}")
    private int retryMaxAttempts;

    @Value("${prism.rabbitmq.listener.gateway.retry.initial-interval:200}")
    private long retryInitialInterval;

    @Value("${prism.rabbitmq.listener.gateway.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${prism.rabbitmq.listener.gateway.retry.max-interval:2000}")
    private long retryMaxInterval;

    @Bean
    public MethodInterceptor gatewayRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(retryMaxAttempts)
                .backOffOptions(retryInitialInterval, retryMultiplier, retryMaxInterval)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory notificationRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("gatewayRetryInterceptor") MethodInterceptor gatewayRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(prefetch);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setAdviceChain(gatewayRetryInterceptor);
        return factory;
    }
}
