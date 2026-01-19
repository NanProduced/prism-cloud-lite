package nan.produced.prism.gateway.realtime.messaging;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.gateway.realtime.sse.SseSessionRegistry;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FrontendEventMqListener {

    private final SseSessionRegistry sseSessionRegistry;

    @RabbitListener(
            queues = GatewayMessagingConstants.Queues.COMMON_NOTIFY,
            containerFactory = "notificationRabbitListenerContainerFactory"
    )
    public void onFrontendEvent(FrontendEventMessage message) {
        if (message == null || message.getScope() == null) {
            return;
        }
        UUID userId = message.getScope().getUserId();
        if (userId == null) {
            log.debug("SSE - skip frontend event without user scope: type={}, traceId={}", message.getType(), message.getTraceId());
            return;
        }

        sseSessionRegistry.sendToUser(userId, message);
    }
}
