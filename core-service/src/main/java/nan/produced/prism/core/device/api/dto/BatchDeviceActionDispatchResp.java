package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "批量下发设备动作结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeviceActionDispatchResp {

    @Schema(description = "批量操作ID（用于消息中心聚合与追踪）", example = "8f3c0d2e-3c5b-4d8c-9f6f-8d1a0b2d7f90")
    private String batchOperationId;

    @Schema(description = "总条数", example = "2")
    private int total;

    @Schema(description = "被接受的条数", example = "2")
    private int accepted;

    @Schema(description = "每台设备的下发结果")
    private List<DeviceActionDispatchResp> results;
}
