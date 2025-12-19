package nan.produced.prism.gateway.realtime.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    public TopicExchange coreNotificationsExchange() {
        return ExchangeBuilder
                .topicExchange(GatewayMessagingConstants.Exchanges.CORE_NOTIFICATIONS)
                .durable(true)
                .build();
    }

    @Bean
    public Queue gatewayNotifyQueue() {
        return QueueBuilder
                .durable(GatewayMessagingConstants.Queues.COMMON_NOTIFY)
                .build();
    }

    @Bean
    public Declarables gatewayNotifyBindings(TopicExchange coreNotificationsExchange,
                                            Queue gatewayNotifyQueue) {
        return new Declarables(
                BindingBuilder.bind(gatewayNotifyQueue)
                        .to(coreNotificationsExchange)
                        .with(GatewayMessagingConstants.RoutingKeys.NOTIFY_ALL)
        );
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}

