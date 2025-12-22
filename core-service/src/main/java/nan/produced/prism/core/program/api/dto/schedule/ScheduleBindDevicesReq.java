package nan.produced.prism.core.program.api.dto.schedule;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class ScheduleBindDevicesReq {

    @NotEmpty
    private List<Long> deviceIds;

    /**
     * When true, replaces existing device->schedule binding.
     * When false, keeps existing binding and reports conflict.
     */
    private Boolean replaceExisting;
}

