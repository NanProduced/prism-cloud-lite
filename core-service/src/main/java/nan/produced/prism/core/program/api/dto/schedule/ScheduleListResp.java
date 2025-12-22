package nan.produced.prism.core.program.api.dto.schedule;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleListResp {

    private UUID scheduleId;

    private String name;

    private String description;

    private Boolean enabled;

    private int boundDevices;

    private int programRules;

    private int commandRules;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}

