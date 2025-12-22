package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.program.domain.ProgramDeploymentStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目部署关系（设备 ⇄ 版本）")
public class ProgramDeploymentResp {

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备名称（冗余，便于 UI 展示）")
    private String deviceName;

    @Schema(description = "发布版本号（平台侧）")
    private Integer releaseVersion;

    @Schema(description = "设备侧节目ID（Colorlight ProgramId）")
    private Integer releaseProgramId;

    @Schema(description = "发布时刻（设备侧 modified 字段）")
    private OffsetDateTime assignedAt;

    @Schema(description = "下载状态（DOWNLOADING/DOWNLOADED）")
    private ProgramDeploymentStatus status;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}

