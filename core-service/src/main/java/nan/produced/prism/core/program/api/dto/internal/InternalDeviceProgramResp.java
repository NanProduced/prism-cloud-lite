package nan.produced.prism.core.program.api.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内部接口：设备侧节目摘要（用于 device-service 适配 /wp-json/wp/v2/programs）。
 *
 * <p>设备侧只认 Integer 节目 ID（release.deviceProgramId），平台侧版本号不会暴露给设备。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "内部接口：设备节目摘要")
public class InternalDeviceProgramResp {

    @Schema(description = "设备侧节目ID（release.deviceProgramId）")
    private Integer deviceProgramId;

    @Schema(description = "设备侧展示标题快照（{programName}-v{version}）")
    private String title;

    @Schema(description = "版本创建时间（release.createdAt）")
    private OffsetDateTime createdAt;

    @Schema(description = "该设备绑定时间（deployment.assignedAt，用于 modified 字段）")
    private OffsetDateTime assignedAt;
}

