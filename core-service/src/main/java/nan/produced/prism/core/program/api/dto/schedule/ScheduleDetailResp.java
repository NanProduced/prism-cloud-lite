package nan.produced.prism.core.program.api.dto.schedule;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleDetailResp {

    private UUID scheduleId;

    private String name;

    private String description;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private List<Long> boundDeviceIds;

    private List<ScheduleContentsRuleResp> contentsRules;

    private List<ScheduleCommandRuleResp> commandRules;
}

