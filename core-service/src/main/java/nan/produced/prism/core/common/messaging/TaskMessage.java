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
public class TaskMessage {

    private String jobId;

    private String jobType;

    private String status;

    private String userId;

    private String triggeredBy;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    private Instant occurredAt;

    private String traceId;

    private String version;
}

