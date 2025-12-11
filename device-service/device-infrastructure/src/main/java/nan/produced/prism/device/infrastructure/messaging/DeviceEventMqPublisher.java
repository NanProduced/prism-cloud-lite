package nan.produced.prism.device.infrastructure.messaging;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.messaging.DeviceEventMessage;
import nan.produced.prism.device.application.port.outbound.event.DeviceEventPublisherPort;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceEventMqPublisher implements DeviceEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(String routingKey, DeviceEventMessage message) {
        DeviceEventMessage payload = enrich(message);
        rabbitTemplate.convertAndSend(DeviceMessagingConstants.DEVICE_EVENTS_EXCHANGE, routingKey, payload);
        if (log.isDebugEnabled()) {
            log.debug("Published device event routingKey={} payload={}", routingKey, payload);
        }
    }

    private DeviceEventMessage enrich(DeviceEventMessage message) {
        DeviceEventMessage payload = message != null ? message : DeviceEventMessage.builder().build();
        if (payload.getOccurredAt() == null) {
            payload.setOccurredAt(Instant.now());
        }
        if (payload.getTraceId() == null) {
            payload.setTraceId(UUID.randomUUID().toString());
        }
        if (payload.getVersion() == null) {
            payload.setVersion(DeviceEventMessage.DEFAULT_VERSION);
        }
        if (payload.getPayload() == null) {
            payload.setPayload(new HashMap<>());
        }
        return payload;
    }
}

