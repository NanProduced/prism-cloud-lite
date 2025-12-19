package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "批量下发设备动作请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeviceActionDispatchReq {

    @Valid
    @NotEmpty
    @Schema(description = "设备动作下发列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<BatchDeviceActionDispatchItemReq> items;
}

