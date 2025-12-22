package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目发布版本（Release）响应")
public class ProgramVersionResp {

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "平台侧版本号（vN）")
    private Integer version;

    @Schema(description = "设备侧节目ID（Colorlight ProgramId）")
    private Integer deviceProgramId;

    @Schema(description = "设备侧展示名快照")
    private String deviceTitleSnapshot;

    @Schema(description = "VSN md5（发布时计算）")
    private String vsnMd5;

    @Schema(description = "VSN 文件大小（bytes）")
    private Long vsnSizeBytes;

    @Schema(description = "版本封面 URL")
    private String coverUrl;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}

