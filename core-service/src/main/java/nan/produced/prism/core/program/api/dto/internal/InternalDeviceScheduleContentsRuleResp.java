package nan.produced.prism.core.program.api.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalDeviceScheduleContentsRuleResp {

    private Integer typePriority;

    private Integer priority;

    private Boolean ifLimitTime;

    private JsonNode limitTime;

    private Boolean ifLimitDate;

    private JsonNode limitDate;

    private Boolean ifLimitWeekday;

    private JsonNode limitWeekday;

    private InternalDeviceScheduleContentsOperationResp operation;

    private String type;

    private String name;
}

