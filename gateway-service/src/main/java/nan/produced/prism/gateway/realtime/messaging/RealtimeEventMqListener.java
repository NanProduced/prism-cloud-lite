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
public class RealtimeEventMqListener {

    private static final String TYPE_SENSOR_REPORTED = "telemetry.sensor.reported";

    private static final String TYPE_GPS_REPORTED = "telemetry.gps.reported";

    private final SseSessionRegistry sseSessionRegistry;

    @RabbitListener(queues = GatewayMessagingConstants.Queues.REALTIME_NOTIFY)
    public void onRealtimeEvent(FrontendEventMessage message) {
        if (message == null || message.getScope() == null) {
            return;
        }
        UUID userId = message.getScope().getUserId();
        if (userId == null) {
            log.debug("SSE - skip realtime event without user scope: type={}, traceId={}", message.getType(), message.getTraceId());
            return;
        }

        String type = message.getType();
        if (TYPE_SENSOR_REPORTED.equals(type)) {
            sseSessionRegistry.sendToMonitoring(userId, message);
            return;
        }
        if (TYPE_GPS_REPORTED.equals(type)) {
            sseSessionRegistry.sendToMap(userId, message);
            return;
        }

        log.debug("SSE - skip unknown realtime event: type={}, traceId={}", type, message.getTraceId());
    }
}

