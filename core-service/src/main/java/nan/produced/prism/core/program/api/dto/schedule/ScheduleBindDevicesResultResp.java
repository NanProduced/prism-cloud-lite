package nan.produced.prism.core.program.api.dto.schedule;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleBindDevicesResultResp {

    private Long deviceId;

    /**
     * bound / no-change / conflict / skip
     */
    private String status;

    private UUID previousScheduleId;
}

