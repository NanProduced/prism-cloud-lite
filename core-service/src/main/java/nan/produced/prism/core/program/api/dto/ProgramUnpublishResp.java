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
@Schema(description = "取消发布响应")
public class ProgramUnpublishResp {

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "目标设备数量（scope 计算后）")
    private int totalTargets;

    @Schema(description = "实际解除绑定数量")
    private int removed;

    @Schema(description = "设备维度结果（包含 dirty 指令下发）")
    private List<ProgramPublishDeviceResultResp> results;
}

