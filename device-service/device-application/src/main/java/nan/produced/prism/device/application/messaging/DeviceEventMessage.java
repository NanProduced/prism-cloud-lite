package nan.produced.prism.device.application.messaging;

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

    public static final String DEFAULT_VERSION = "1.0";

    private Long deviceId;

    private String eventType;

    /**
     * report.* 特有的上报数据
     */
    private String reportData;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    private Instant occurredAt;

    @Builder.Default
    private boolean retryable = false;

    private String traceId;

    @Builder.Default
    private String version = DEFAULT_VERSION;
}

