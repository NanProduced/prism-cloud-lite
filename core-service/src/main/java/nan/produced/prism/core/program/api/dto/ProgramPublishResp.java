package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目发布响应")
public class ProgramPublishResp {

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "目标版本号（平台侧）")
    private Integer version;

    @Schema(description = "目标设备侧节目ID（Colorlight ProgramId）")
    private Integer deviceProgramId;

    @Schema(description = "目标设备数量（scope 计算后）")
    private int totalTargets;

    @Schema(description = "实际影响设备数量（排除 append 已存在/空目标等）")
    private int affected;

    @Schema(description = "设备维度动作与下发结果")
    private List<ProgramPublishDeviceResultResp> results;
}

