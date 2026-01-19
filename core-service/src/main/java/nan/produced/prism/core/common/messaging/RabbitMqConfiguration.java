package nan.produced.prism.core.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    @Bean
    public TopicExchange deviceEventsDlxExchange() {
        return ExchangeBuilder
                .topicExchange(MessagingConstants.Exchanges.DEVICE_EVENTS_DLX)
                .durable(true).build();
    }

    @Bean
    public TopicExchange coreNotificationsDlxExchange() {
        return ExchangeBuilder
                .topicExchange(MessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .durable(true).build();
    }

    @Bean
    public TopicExchange coreNotificationsErrorExchange() {
        return ExchangeBuilder
                .topicExchange(MessagingConstants.Exchanges.CORE_NOTIFICATIONS_ERROR)
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
                .withArgument("x-dead-letter-exchange", MessagingConstants.Exchanges.DEVICE_EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", MessagingConstants.RoutingKeys.DLQ_DEVICE_STATUS)
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
                .withArgument("x-dead-letter-exchange", MessagingConstants.Exchanges.DEVICE_EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", MessagingConstants.RoutingKeys.DLQ_DEVICE_COMMAND)
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
                .withArgument("x-dead-letter-exchange", MessagingConstants.Exchanges.DEVICE_EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", MessagingConstants.RoutingKeys.DLQ_DEVICE_REPORT)
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
     * 创建导出任务队列（独立于 core-task-worker-q）
     */
    @Bean
    public Queue coreExportWorkerQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.EXPORT_WORKER)
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
                .withArgument("x-dead-letter-exchange", MessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", MessagingConstants.RoutingKeys.DLQ_NOTIFY)
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
                .withArgument("x-dead-letter-exchange", MessagingConstants.Exchanges.CORE_NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", MessagingConstants.RoutingKeys.DLQ_REALTIME)
                .build();
    }

    @Bean
    public Queue coreDeviceStatusDlqQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_STATUS_DLQ)
                .build();
    }

    @Bean
    public Queue coreDeviceCommandDlqQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_COMMAND_DLQ)
                .build();
    }

    @Bean
    public Queue coreDeviceReportDlqQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.DEVICE_REPORT_DLQ)
                .build();
    }

    @Bean
    public Queue coreNotifyDlqQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.COMMON_NOTIFY_DLQ)
                .build();
    }

    @Bean
    public Queue coreRealtimeDlqQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.REALTIME_NOTIFY_DLQ)
                .build();
    }

    @Bean
    public Queue coreTaskErrorQueue() {
        return QueueBuilder
                .durable(MessagingConstants.Queues.TASK_ERROR)
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

    @Bean
    public Declarables deviceEventsDlqBindings(@Qualifier("deviceEventsDlxExchange") TopicExchange deviceEventsDlxExchange,
                                              @Qualifier("coreDeviceStatusDlqQueue") Queue coreDeviceStatusDlqQueue,
                                              @Qualifier("coreDeviceCommandDlqQueue") Queue coreDeviceCommandDlqQueue,
                                              @Qualifier("coreDeviceReportDlqQueue") Queue coreDeviceReportDlqQueue) {
        return new Declarables(
            BindingBuilder.bind(coreDeviceStatusDlqQueue)
                .to(deviceEventsDlxExchange)
                .with(MessagingConstants.RoutingKeys.DLQ_DEVICE_STATUS),
            BindingBuilder.bind(coreDeviceCommandDlqQueue)
                .to(deviceEventsDlxExchange)
                .with(MessagingConstants.RoutingKeys.DLQ_DEVICE_COMMAND),
            BindingBuilder.bind(coreDeviceReportDlqQueue)
                .to(deviceEventsDlxExchange)
                .with(MessagingConstants.RoutingKeys.DLQ_DEVICE_REPORT)
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
                                                @Qualifier("coreExportWorkerQueue") Queue coreExportWorkerQueue,
                                                @Qualifier("coreNotifyQueue") Queue coreNotifyQueue,
                                                @Qualifier("coreRealtimeQueue") Queue coreRealtimeQueue) {
        return new Declarables(
            BindingBuilder.bind(coreTaskWorkerQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_PENDING),
            BindingBuilder.bind(coreExportWorkerQueue)
                .to(coreNotificationsExchange)
                .with(MessagingConstants.RoutingKeys.TASK_EXPORT_PENDING),
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
    public Declarables coreNotificationDlqBindings(@Qualifier("coreNotificationsDlxExchange") TopicExchange coreNotificationsDlxExchange,
                                                   @Qualifier("coreNotifyDlqQueue") Queue coreNotifyDlqQueue,
                                                   @Qualifier("coreRealtimeDlqQueue") Queue coreRealtimeDlqQueue) {
        return new Declarables(
            BindingBuilder.bind(coreNotifyDlqQueue)
                .to(coreNotificationsDlxExchange)
                .with(MessagingConstants.RoutingKeys.DLQ_NOTIFY),
            BindingBuilder.bind(coreRealtimeDlqQueue)
                .to(coreNotificationsDlxExchange)
                .with(MessagingConstants.RoutingKeys.DLQ_REALTIME)
        );
    }

    @Bean
    public Declarables coreTaskErrorBindings(@Qualifier("coreNotificationsErrorExchange") TopicExchange coreNotificationsErrorExchange,
                                             @Qualifier("coreTaskErrorQueue") Queue coreTaskErrorQueue) {
        return new Declarables(
            BindingBuilder.bind(coreTaskErrorQueue)
                .to(coreNotificationsErrorExchange)
                .with(MessagingConstants.RoutingKeys.TASK_ERROR)
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
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                if (log.isDebugEnabled()) {
                    log.debug("RabbitMQ confirm ack: correlationId={}",
                            correlationData != null ? correlationData.getId() : "null");
                }
                return;
            }
            log.warn("RabbitMQ confirm nack: correlationId={} cause={}",
                    correlationData != null ? correlationData.getId() : "null",
                    cause);
        });
        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "RabbitMQ return: exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
        return rabbitTemplate;
    }
}
