package nan.produced.prism.core.program.api.dto.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleCommandRuleResp {

    private Long id;

    private UUID scheduleId;

    private JsonNode payload;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}

