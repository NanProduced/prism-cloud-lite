package nan.produced.prism.core.program.api.dto.schedule;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SchedulePushResp {

    private int totalTargets;

    private int accepted;

    private List<SchedulePushResultResp> results;
}

