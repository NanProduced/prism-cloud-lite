package nan.produced.prism.core.program.api.dto.schedule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SchedulePushResultResp {

    private Long deviceId;

    private String commandId;

    private boolean accepted;

    private Integer queuedId;

    private String errorMessage;
}

