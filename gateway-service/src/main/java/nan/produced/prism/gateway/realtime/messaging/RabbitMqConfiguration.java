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
import org.springframework.beans.factory.annotation.Qualifier;
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
    public TopicExchange coreNotificationsDlxExchange() {
        return ExchangeBuilder
                .topicExchange(GatewayMessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .durable(true)
                .build();
    }

    @Bean
    public Queue gatewayNotifyQueue() {
        return QueueBuilder
                .durable(GatewayMessagingConstants.Queues.COMMON_NOTIFY)
                .withArgument("x-dead-letter-exchange", GatewayMessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", GatewayMessagingConstants.RoutingKeys.DLQ_NOTIFY)
                .build();
    }

    @Bean
    public Queue gatewayRealtimeQueue() {
        return QueueBuilder
                .durable(GatewayMessagingConstants.Queues.REALTIME_NOTIFY)
                .withArgument("x-message-ttl", 60000)
                .withArgument("x-max-length", 10000)
                .withArgument("x-dead-letter-exchange", GatewayMessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", GatewayMessagingConstants.RoutingKeys.DLQ_REALTIME)
                .build();
    }

    @Bean
    public Queue gatewayNotifyDlqQueue() {
        return QueueBuilder
                .durable(GatewayMessagingConstants.Queues.COMMON_NOTIFY_DLQ)
                .build();
    }

    @Bean
    public Queue gatewayRealtimeDlqQueue() {
        return QueueBuilder
                .durable(GatewayMessagingConstants.Queues.REALTIME_NOTIFY_DLQ)
                .build();
    }

    @Bean
    public Declarables gatewayNotifyBindings(@Qualifier("coreNotificationsExchange") TopicExchange coreNotificationsExchange,
                                             @Qualifier("gatewayNotifyQueue") Queue gatewayNotifyQueue,
                                             @Qualifier("gatewayRealtimeQueue") Queue gatewayRealtimeQueue) {
        return new Declarables(
                BindingBuilder.bind(gatewayNotifyQueue)
                        .to(coreNotificationsExchange)
                        .with(GatewayMessagingConstants.RoutingKeys.NOTIFY_ALL),
                BindingBuilder.bind(gatewayRealtimeQueue)
                        .to(coreNotificationsExchange)
                        .with(GatewayMessagingConstants.RoutingKeys.REALTIME_ALL)
        );
    }

    @Bean
    public Declarables gatewayNotifyDlqBindings(@Qualifier("coreNotificationsDlxExchange") TopicExchange coreNotificationsDlxExchange,
                                                @Qualifier("gatewayNotifyDlqQueue") Queue gatewayNotifyDlqQueue,
                                                @Qualifier("gatewayRealtimeDlqQueue") Queue gatewayRealtimeDlqQueue) {
        return new Declarables(
                BindingBuilder.bind(gatewayNotifyDlqQueue)
                        .to(coreNotificationsDlxExchange)
                        .with(GatewayMessagingConstants.RoutingKeys.DLQ_NOTIFY),
                BindingBuilder.bind(gatewayRealtimeDlqQueue)
                        .to(coreNotificationsDlxExchange)
                        .with(GatewayMessagingConstants.RoutingKeys.DLQ_REALTIME)
        );
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
