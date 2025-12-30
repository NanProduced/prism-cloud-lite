package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通过 VSN 文件名解析得到的节目版本信息")
public class ProgramResolveByVsnResp {

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "节目名称（若已删除则可能为空）")
    private String programName;

    @Schema(description = "平台侧版本号（vN）")
    private Integer version;

    @Schema(description = "设备侧节目ID（Colorlight ProgramId）")
    private Integer releaseProgramId;

    @Schema(description = "VSN md5（小写）")
    private String vsnMd5;

    @Schema(description = "VSN 文件大小（bytes）")
    private Long vsnSizeBytes;
}

