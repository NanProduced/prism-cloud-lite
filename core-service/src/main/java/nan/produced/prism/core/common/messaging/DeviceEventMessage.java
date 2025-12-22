package nan.produced.prism.core.common.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceEventMessage {

    private Long deviceId;

    private String eventType;

    /**
     * report.* 特有的上报数据（device-service 将原始 JSON 透传为字符串）
     */
    private String reportData;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    private Instant occurredAt;

    private boolean retryable;

    private String traceId;

    private String version;
}
