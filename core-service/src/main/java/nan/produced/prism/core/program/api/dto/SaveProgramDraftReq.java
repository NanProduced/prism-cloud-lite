package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存节目草稿请求（真保存）")
public class SaveProgramDraftReq {

    @NotBlank
    @Schema(description = "VSN JSON（编辑器状态快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String vsnJson;

    @Schema(description = "封面截图 base64（可为 dataURL 或纯 base64）")
    private String coverBase64;

    @Schema(description = "封面 MIME 类型（coverBase64 非 dataURL 时建议填写）", example = "image/png")
    private String coverContentType;

    @Schema(description = "内容 hash（可选，用于更准确的未发布判断）")
    private String contentHash;
}

