package nan.produced.prism.core.common.messaging;

import java.time.Instant;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishDeviceEvent(String routingKey, DeviceEventMessage message) {
        DeviceEventMessage payload = prepareDeviceEvent(message);
        send(MessagingConstants.Exchanges.DEVICE_EVENTS, routingKey, payload);
    }

    public void publishCoreNotification(String routingKey, FrontendEventMessage message) {
        FrontendEventMessage payload = prepareFrontendEventMessage(message);
        send(MessagingConstants.Exchanges.CORE_NOTIFICATIONS, routingKey, payload);
    }

    private DeviceEventMessage prepareDeviceEvent(DeviceEventMessage message) {
        DeviceEventMessage payload = message != null ? message : DeviceEventMessage.builder().build();
        if (payload.getOccurredAt() == null) {
            payload.setOccurredAt(Instant.now());
        }
        if (payload.getTraceId() == null) {
            payload.setTraceId(TraceUtils.getTraceId());
        }
        if (payload.getVersion() == null) {
            payload.setVersion(MessagingConstants.MESSAGE_VERSION);
        }
        if (payload.getPayload() == null) {
            payload.setPayload(new HashMap<>());
        }
        return payload;
    }

    private FrontendEventMessage prepareFrontendEventMessage(FrontendEventMessage message) {
        FrontendEventMessage payload = message != null ? message : FrontendEventMessage.builder().build();
        if (payload.getOccurredAt() == null) {
            payload.setOccurredAt(Instant.now());
        }
        if (payload.getTraceId() == null) {
            payload.setTraceId(TraceUtils.getTraceId());
        }
        if (payload.getVersion() == null) {
            payload.setVersion(MessagingConstants.MESSAGE_VERSION);
        }
        if (payload.getData() == null) {
            payload.setData(new HashMap<>());
        }
        return payload;
    }

    private void send(String exchange, String routingKey, Object payload) {
        Assert.hasText(routingKey, "routingKey must not be blank");
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        if (log.isDebugEnabled()) {
            log.debug("Published message exchange={} routingKey={} payload={}", exchange, routingKey, payload);
        }
    }
}
