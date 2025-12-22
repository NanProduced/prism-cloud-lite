package nan.produced.prism.core.program.api.dto.schedule;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduleBindingDeviceResp {

    private Long deviceId;

    private String deviceName;

    private Integer onlineStatus;

    private OffsetDateTime boundAt;
}

