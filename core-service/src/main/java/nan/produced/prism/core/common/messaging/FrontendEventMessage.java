package nan.produced.prism.core.common.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面向前端（SPA）的通用事件消息
 * <p>
 * 用于 core-service 发布到 core.notifications（notify.*），由 gateway-service 消费并推送 SSE。
 * <p>
 * 设计对齐 BffResponse 的 success/data/error/traceId 结构，并增加 type/scope/occurredAt/version 等事件元信息。
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FrontendEventMessage {

    /**
     * 是否成功（对齐 BffResponse）
     */
    private boolean success;

    /**
     * 事件类型（多路复用）
     * <p>示例：device.status.changed / operation.updated</p>
     */
    private String type;

    /**
     * 事件作用域（用于前端快速过滤/分发）
     */
    private Scope scope;

    /**
     * 事件数据（成功时有值）
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 错误信息（失败时有值）
     */
    private ErrorDetail error;

    /**
     * 事件发生时间
     */
    private Instant occurredAt;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 消息版本
     */
    private String version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Scope {

        private UUID userId;

        private Long deviceId;

        private String operationId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {

        private String code;

        private String message;

        private String displayMessage;

        private boolean retryable;
    }
}

