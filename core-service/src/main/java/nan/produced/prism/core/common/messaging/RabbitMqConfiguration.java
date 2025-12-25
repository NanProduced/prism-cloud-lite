package nan.produced.prism.core.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    /**
     * 创建设备事件交换机
     * @return 设备事件交换机
     */
    @Bean
    public TopicExchange deviceEventsExchange() {
        return ExchangeBuilder
                .topicExchange(MessagingConstants.Exchanges.DEVICE_EVENTS)
                .durable(true).build();
    }

    /**
     * 创建业务事件通知交换机
     * @return 业务事件交换机
     */
    @Bean
    public TopicExchange coreNotificationsExchange() {
        return ExchangeBuilder
                .topicExchange(MessagingConstants.Exchanges.CORE_NOTIFICATIONS)
                .durable(true).build();
    }

    /**
     * 创建设备在线状态队列
     * @return 设备在线状态队列
     */
    @Bean
    public Queue coreDeviceStatusQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_STATUS)
                .build();
    }

    /**
     * 创建设备指令响应队列
     * @return 设备指令队列
     */
    @Bean
    public Queue coreDeviceCommandQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_COMMAND)
                .build();
    }

    /**
     * 创建设备上报数据队列
     * @return 设备上报数据队列
     */
    @Bean
    public Queue coreDeviceReportQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_REPORT)
                .build();
    }

    /**
     * 创建业务异步任务队列
     * @return 任务队列
     */
    @Bean
    public Queue coreTaskWorkerQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.TASK_WORKER)
                .withArgument("x-max-priority", 10)
                .build();
    }

    /**
     * 创建spa通知队列
     * @return 通知队列
     */
    @Bean
    public Queue coreNotifyQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.COMMON_NOTIFY)
                .build();
    }

    /**
     * 创建高频实时数据队列（仅用于 SSE 推送，不落库到消息中心）
     */
    @Bean
    public Queue coreRealtimeQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.REALTIME_NOTIFY)
                .withArgument("x-message-ttl", 60000)
                .withArgument("x-max-length", 10000)
                .build();
    }

    /**
     * 创建设备事件绑定
     * @param deviceEventsExchange 设备事件交换机
     * @param coreDeviceStatusQueue 设备在线状态队列
     * @param coreDeviceCommandQueue 设备指令结果队列
     * @param coreDeviceReportQueue 设备上报数据队列
     * @return 设备事件绑定
     */
    @Bean
    public Declarables deviceEventsBindings(@Qualifier("deviceEventsExchange") TopicExchange deviceEventsExchange,
                                           @Qualifier("coreDeviceStatusQueue") Queue coreDeviceStatusQueue,
                                           @Qualifier("coreDeviceCommandQueue") Queue coreDeviceCommandQueue,
                                           @Qualifier("coreDeviceReportQueue") Queue coreDeviceReportQueue) {
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

    /**
     * 创建业务通知绑定
     * @param coreNotificationsExchange 业务通知交换机
     * @param coreTaskWorkerQueue 业务异步任务队列
     * @param coreNotifyQueue 通知队列
     * @return 业务通知绑定
     */
    @Bean
    public Declarables coreNotificationBindings(@Qualifier("coreNotificationsExchange") TopicExchange coreNotificationsExchange,
                                                @Qualifier("coreTaskWorkerQueue") Queue coreTaskWorkerQueue,
                                                @Qualifier("coreNotifyQueue") Queue coreNotifyQueue,
                                                @Qualifier("coreRealtimeQueue") Queue coreRealtimeQueue) {
        return new Declarables(
            BindingBuilder.bind(coreTaskWorkerQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_PENDING),
            BindingBuilder.bind(coreNotifyQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_RESULT),
            BindingBuilder.bind(coreNotifyQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.NOTIFY_ALL),
            BindingBuilder.bind(coreRealtimeQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.REALTIME_ALL)
        );
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
