package nan.produced.prism.core.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    public TopicExchange deviceEventsExchange() {
        return ExchangeBuilder.topicExchange(MessagingConstants.Exchanges.DEVICE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange coreNotificationsExchange() {
        return ExchangeBuilder.topicExchange(MessagingConstants.Exchanges.CORE_NOTIFICATIONS).durable(true).build();
    }

    @Bean
    public Queue coreDeviceStatusQueue() {
        return QueueBuilder.durable(MessagingConstants.Queues.DEVICE_STATUS).build();
    }

    @Bean
    public Queue coreDeviceCommandQueue() {
        return QueueBuilder.durable(MessagingConstants.Queues.DEVICE_COMMAND).build();
    }

    @Bean
    public Queue coreDeviceReportQueue() {
        return QueueBuilder.durable(MessagingConstants.Queues.DEVICE_REPORT).build();
    }

    @Bean
    public Queue coreTaskWorkerQueue() {
        return QueueBuilder.durable(MessagingConstants.Queues.TASK_WORKER).build();
    }

    @Bean
    public Queue coreNotifyQueue() {
        return QueueBuilder.durable(MessagingConstants.Queues.COMMON_NOTIFY).build();
    }

    @Bean
    public Declarables deviceEventsBindings(TopicExchange deviceEventsExchange,
                                           Queue coreDeviceStatusQueue,
                                           Queue coreDeviceCommandQueue,
                                           Queue coreDeviceReportQueue) {
        return new Declarables(
            BindingBuilder.bind(coreDeviceStatusQueue)
                .to(deviceEventsExchange)
                .with(MessagingConstants.RoutingKeys.STATUS_ALL),
            BindingBuilder.bind(coreDeviceCommandQueue)
                .to(deviceEventsExchange)
                .with(MessagingConstants.RoutingKeys.COMMAND_ALL),
            BindingBuilder.bind(coreDeviceReportQueue)
                .to(deviceEventsExchange)
                .with(MessagingConstants.RoutingKeys.REPORT_ALL)
        );
    }

    @Bean
    public Declarables coreNotificationBindings(TopicExchange coreNotificationsExchange,
                                                Queue coreTaskWorkerQueue,
                                                Queue coreNotifyQueue) {
        return new Declarables(
            BindingBuilder.bind(coreTaskWorkerQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_PENDING),
            BindingBuilder.bind(coreNotifyQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_RESULT),
            BindingBuilder.bind(coreNotifyQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.NOTIFY_ALL)
        );
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }
}

