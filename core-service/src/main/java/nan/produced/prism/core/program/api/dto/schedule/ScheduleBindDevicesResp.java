package nan.produced.prism.core.program.api.dto.schedule;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleBindDevicesResp {

    private int totalTargets;

    private int bound;

    private int conflicts;

    private List<ScheduleBindDevicesResultResp> results;
}

