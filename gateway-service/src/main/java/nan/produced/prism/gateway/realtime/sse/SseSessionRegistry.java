package nan.produced.prism.gateway.realtime.sse;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.gateway.realtime.messaging.FrontendEventMessage;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class SseSessionRegistry {

    private static final long SSE_TIMEOUT_MS = 0L;

    private static final String SSE_EVENT_NAME = "prism";

    private final Map<UUID, Map<String, SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    
    private final Object monitor = new Object();

    public SseEmitter register(UUID userId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emittersByUser
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);

        emitter.onCompletion(() -> remove(userId, connectionId));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(userId, connectionId);
        });
        emitter.onError(ex -> {
            remove(userId, connectionId);
            log.debug("SSE - connection error: userId={}, connectionId={}", userId, connectionId, ex);
        });

        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(SSE_EVENT_NAME)
                        .comment("connected")
                        .reconnectTime(3000));
            }
        } catch (IOException ex) {
            remove(userId, connectionId);
            emitter.completeWithError(ex);
        }

        log.debug("SSE - connected: userId={}, connectionId={}", userId, connectionId);
        return emitter;
    }

    public void sendToUser(UUID userId, FrontendEventMessage message) {
        if (userId == null || message == null) {
            return;
        }
        Map<String, SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        emitters.forEach((connectionId, emitter) -> {
            try {
                synchronized (monitor) {
                    emitter.send(SseEmitter.event()
                            .name(SSE_EVENT_NAME)
                            .id(message.getTraceId())
                            .data(message, MediaType.APPLICATION_JSON));
                }
            } catch (Exception ex) {
                log.debug("SSE - send failed, removing connection: userId={}, connectionId={}", userId, connectionId, ex);
                remove(userId, connectionId);
                emitter.complete();
            }
        });
    }

    @Scheduled(fixedDelayString = "${prism.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        emittersByUser.forEach((userId, emitters) -> emitters.forEach((connectionId, emitter) -> {
            try {
                synchronized (monitor) {
                    emitter.send(SseEmitter.event().comment("ping"));
                }
            } catch (Exception ex) {
                log.debug("SSE - heartbeat failed, removing connection: userId={}, connectionId={}", userId, connectionId, ex);
                remove(userId, connectionId);
                emitter.complete();
            }
        }));
    }

    private void remove(UUID userId, String connectionId) {
        Map<String, SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(connectionId);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
