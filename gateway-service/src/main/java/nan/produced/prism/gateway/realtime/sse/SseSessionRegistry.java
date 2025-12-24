package nan.produced.prism.gateway.realtime.sse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private final Map<UUID, Map<String, SseConnection>> connectionsByUser = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        return register(userId, Channel.GLOBAL, null);
    }

    public SseEmitter registerMonitoring(UUID userId, Set<Long> deviceIds) {
        return register(userId, Channel.MONITORING, normalizeDeviceIds(deviceIds));
    }

    public SseEmitter registerMap(UUID userId, Long deviceId) {
        Set<Long> deviceIds = deviceId != null ? Set.of(deviceId) : Collections.emptySet();
        return register(userId, Channel.MAP, deviceIds);
    }

    private SseEmitter register(UUID userId, Channel channel, Set<Long> deviceIds) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        connectionsByUser
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, new SseConnection(emitter, channel, deviceIds));

        emitter.onCompletion(() -> remove(userId, connectionId));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(userId, connectionId);
        });
        emitter.onError(ex -> {
            remove(userId, connectionId);
            log.debug("SSE - connection error: userId={}, connectionId={}, channel={}", userId, connectionId, channel, ex);
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

        log.debug("SSE - connected: userId={}, connectionId={}, channel={}", userId, connectionId, channel);
        return emitter;
    }

    public void sendToUser(UUID userId, FrontendEventMessage message) {
        sendToChannel(userId, Channel.GLOBAL, message);
    }

    public void sendToMonitoring(UUID userId, FrontendEventMessage message) {
        sendToChannel(userId, Channel.MONITORING, message);
    }

    public void sendToMap(UUID userId, FrontendEventMessage message) {
        sendToChannel(userId, Channel.MAP, message);
    }

    private void sendToChannel(UUID userId, Channel channel, FrontendEventMessage message) {
        if (userId == null || message == null) {
            return;
        }
        Map<String, SseConnection> connections = connectionsByUser.get(userId);
        if (connections == null || connections.isEmpty()) {
            return;
        }

        Long deviceId = message.getScope() != null ? message.getScope().getDeviceId() : null;
        connections.forEach((connectionId, connection) -> {
            if (connection == null || connection.channel() != channel) {
                return;
            }
            if ((channel == Channel.MONITORING || channel == Channel.MAP) && !connection.acceptsDevice(deviceId)) {
                return;
            }
            SseEmitter emitter = connection.emitter();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event()
                            .name(SSE_EVENT_NAME)
                            .id(message.getTraceId())
                            .data(message, MediaType.APPLICATION_JSON));
                }
            } catch (Exception ex) {
                log.debug("SSE - send failed, removing connection: userId={}, connectionId={}, channel={}",
                        userId, connectionId, channel, ex);
                remove(userId, connectionId);
                emitter.complete();
            }
        });
    }

    @Scheduled(fixedDelayString = "${prism.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        connectionsByUser.forEach((userId, connections) -> connections.forEach((connectionId, connection) -> {
            if (connection == null) {
                return;
            }
            SseEmitter emitter = connection.emitter();
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().comment("ping"));
                }
            } catch (Exception ex) {
                log.debug("SSE - heartbeat failed, removing connection: userId={}, connectionId={}, channel={}",
                        userId, connectionId, connection.channel(), ex);
                remove(userId, connectionId);
                emitter.complete();
            }
        }));
    }

    private void remove(UUID userId, String connectionId) {
        Map<String, SseConnection> connections = connectionsByUser.get(userId);
        if (connections == null) {
            return;
        }
        connections.remove(connectionId);
        if (connections.isEmpty()) {
            connectionsByUser.remove(userId);
        }
    }

    private Set<Long> normalizeDeviceIds(Set<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> normalized = new HashSet<>();
        for (Long deviceId : deviceIds) {
            if (deviceId != null && deviceId > 0) {
                normalized.add(deviceId);
            }
        }
        return normalized.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(normalized);
    }

    private enum Channel {
        GLOBAL,
        MONITORING,
        MAP
    }

    private record SseConnection(SseEmitter emitter, Channel channel, Set<Long> deviceIds) {

        private boolean acceptsDevice(Long deviceId) {
            if (deviceIds == null || deviceIds.isEmpty()) {
                return false;
            }
            if (deviceId == null) {
                return false;
            }
            return deviceIds.contains(deviceId);
        }
    }
}
