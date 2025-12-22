package nan.produced.prism.core.program.api.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalDeviceScheduleCommandRuleResp {

    private InternalDeviceScheduleCommandOperationResp operation;

    private List<String> opTime;

    private Boolean ifLimitDate;

    private InternalDeviceScheduleLimitDateResp limitDate;

    private Boolean ifLimitWeekday;

    private List<Boolean> limitWeekday;

    private InternalDeviceScheduleCommandContentResp content;

    private String type;

    private String name;
}

