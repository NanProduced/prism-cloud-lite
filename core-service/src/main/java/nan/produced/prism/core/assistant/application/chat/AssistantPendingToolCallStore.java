package nan.produced.prism.core.assistant.application.chat;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssistantPendingToolCallStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<UUID, PendingToolCall> pendingByUserId = new ConcurrentHashMap<>();

    public record PendingToolCall(String toolCallId, String toolName, Instant createdAt) {
        boolean isExpired(Duration ttl) {
            if (createdAt == null) {
                return true;
            }
            Duration effective = ttl != null ? ttl : DEFAULT_TTL;
            return createdAt.plus(effective).isBefore(Instant.now());
        }
    }

    public PendingToolCall get(UUID userId) {
        if (userId == null) {
            return null;
        }
        PendingToolCall pending = pendingByUserId.get(userId);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(DEFAULT_TTL)) {
            pendingByUserId.remove(userId, pending);
            return null;
        }
        return pending;
    }

    public void set(UUID userId, String toolCallId, String toolName) {
        if (userId == null || toolCallId == null || toolCallId.isBlank()) {
            return;
        }
        pendingByUserId.put(userId, new PendingToolCall(toolCallId, toolName, Instant.now()));
    }

    public void clear(UUID userId) {
        if (userId == null) {
            return;
        }
        pendingByUserId.remove(userId);
    }
}

