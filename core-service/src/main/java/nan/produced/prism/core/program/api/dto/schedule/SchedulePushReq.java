package nan.produced.prism.core.program.api.dto.schedule;

import java.util.List;
import lombok.Data;

@Data
public class SchedulePushReq {

    /**
     * Optional. When omitted or empty, defaults to all bound devices.
     */
    private List<Long> deviceIds;
}

