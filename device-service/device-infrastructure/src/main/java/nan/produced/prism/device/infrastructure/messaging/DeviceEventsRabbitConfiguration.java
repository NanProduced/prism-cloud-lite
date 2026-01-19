package nan.produced.prism.device.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置
 *
 * @author Nan
 */
@Configuration
@Slf4j
public class DeviceEventsRabbitConfiguration {

    /**
     * 创建设备事件交换机
     * @return 设备事件交换机
     */
    @Bean
    public TopicExchange deviceEventsExchange() {
        return ExchangeBuilder
                .topicExchange(DeviceMessagingConstants.DEVICE_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }


    @Bean
    public Declarables deviceEventsDeclarables(TopicExchange deviceEventsExchange) {
        return new Declarables(deviceEventsExchange);
    }

    @Bean
    public MessageConverter deviceMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate deviceRabbitTemplate(ConnectionFactory connectionFactory,
                                               MessageConverter deviceMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(deviceMessageConverter);
        // “强制路由”模式,交换机无法找到任何一个匹配的队列（路由失败），RabbitMQ 会将消息退回给生产者（触发 ReturnCallback）
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

