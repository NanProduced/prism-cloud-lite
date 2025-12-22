package nan.produced.prism.core.program.api.dto.schedule;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceScheduleResp {

    private Long deviceId;

    private UUID scheduleId;

    private String scheduleName;

    private String scheduleDescription;

    private Boolean scheduleEnabled;

    private OffsetDateTime scheduleCreatedAt;

    private OffsetDateTime scheduleUpdatedAt;

    private OffsetDateTime boundAt;

    private int programRulesCount;

    private int commandRulesCount;

    private List<ScheduleContentsRuleResp> contentsRules;

    private List<ScheduleCommandRuleResp> commandRules;
}
