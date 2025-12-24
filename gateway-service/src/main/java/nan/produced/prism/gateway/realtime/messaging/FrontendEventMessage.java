package nan.produced.prism.gateway.realtime.messaging;

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
 * 从 core.notifications（notify.#）消费的前端事件消息
 * <p>
 * gateway-service 负责按 userId 路由并推送 SSE。
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FrontendEventMessage {

    private boolean success;

    private String type;

    private Scope scope;

    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    private ErrorDetail error;

    private Instant occurredAt;

    private String traceId;

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
