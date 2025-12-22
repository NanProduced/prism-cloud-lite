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
@Schema(description = "节目草稿响应")
public class ProgramDraftResp {

    @Schema(description = "草稿ID")
    private UUID draftId;

    @Schema(description = "节目ID")
    private UUID programId;

    @Schema(description = "草稿基线版本；0=Blank, N=from vN")
    private Integer baseVersion;

    @Schema(description = "VSN JSON（编辑器快照）")
    private String vsnJson;

    @Schema(description = "草稿封面 URL（用于列表未发布场景）")
    private String coverUrl;

    @Schema(description = "内容 hash（可选）")
    private String contentHash;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}

