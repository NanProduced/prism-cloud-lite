package nan.produced.prism.core.device.api.dto;

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
@Schema(description = "删除设备上指定节目响应（可能会对多个 source 进行尝试）")
public class DeleteDeviceProgramResp {

    @Schema(description = "平台节目ID（若按 programId 删除则有值）")
    private UUID programId;

    @Schema(description = "最终删除的 VSN 文件名（用于审计/排障）")
    private String vsnName;

    @Schema(description = "尝试的 source 列表（按顺序）")
    private List<String> sources;

    @Schema(description = "每次下发的结果（每个 source 一条 operation）")
    private List<DeviceActionDispatchResp> results;
}

