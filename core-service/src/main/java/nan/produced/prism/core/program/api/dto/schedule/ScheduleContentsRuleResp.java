package nan.produced.prism.core.program.api.dto.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleContentsRuleResp {

    private Long id;

    private UUID scheduleId;

    private String type;

    private Integer priority;

    private Integer releaseProgramId;

    private UUID programId;

    private Integer releaseVersion;

    private String deviceTitleSnapshot;

    private Boolean ifLimitTime;

    private JsonNode limitTime;

    private Boolean ifLimitDate;

    private JsonNode limitDate;

    private Boolean ifLimitWeekday;

    private JsonNode limitWeekday;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}

