package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目发布请求（Publish Stepper 最终确认）")
public class ProgramPublishReq {

    @NotNull
    @Schema(description = "版本模式：CREATE/EXISTING", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProgramPublishVersionMode versionMode;

    @Schema(description = "草稿ID（versionMode=CREATE 时建议传入）")
    private UUID draftId;

    @Schema(description = "VSN JSON（versionMode=CREATE 且不使用 draftId 时传入）")
    private String vsnJson;

    @Schema(description = "封面截图 base64（可选：发布时覆盖版本封面；不传则使用 draft 封面）")
    private String coverBase64;

    @Schema(description = "封面 MIME 类型（coverBase64 非 dataURL 时建议填写）")
    private String coverContentType;

    @Schema(description = "既有版本号（versionMode=EXISTING 时必须传）")
    private Integer existingVersion;

    @NotNull
    @Schema(description = "范围：SELECTED/RUNNING", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProgramPublishScope scope;

    @NotNull
    @Schema(description = "模式：APPEND/OVERWRITE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProgramPublishMode mode;

    @Schema(description = "目标设备ID集合（scope=SELECTED 时使用；scope=RUNNING 时可不传）")
    private List<Long> deviceIds;
}

