package nan.produced.prism.core.assistant.application.chat;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储用户正在使用的工具调用
 * <P>前端交互型工具挂起状态存储</P>
 * <p>当后端向前端发出需要用户交互的工具（如 pickDevice / pickCommandLog）时，会结束本轮 SSE（finishReason:"tool-calls"），等待前端把用户选择通过 addToolOutput 回传。AssistantPendingToolCallStore 就是记录“当前用户尚未回传的 toolCallId + toolName”，并在下一次请求到来时校验/提取用的。</p>
 */
@Component
public class AssistantPendingToolCallStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * <p>key: UserId</p>
     * <p>value: 待处理的 toolCallId, toolName, 创建时间</p>
     * <p>TTL: 10 分钟</p>
     */
    private final ConcurrentHashMap<UUID, PendingToolCall> pendingByUserId = new ConcurrentHashMap<>();

    /**
     * 待处理的工具调用
     * @param toolCallId 工具调用 id
     * @param toolName 工具名称
     * @param createdAt 创建时间
     */
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

