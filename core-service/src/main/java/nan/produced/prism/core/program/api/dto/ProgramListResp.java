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
@Schema(description = "节目列表项")
public class ProgramListResp {

    @Schema(description = "节目ID")
    private UUID id;

    @Schema(description = "节目名称")
    private String name;

    @Schema(description = "画布宽度")
    private Integer width;

    @Schema(description = "画布高度")
    private Integer height;

    @Schema(description = "默认版本")
    private Integer defaultVersion;

    @Schema(description = "最新发布版本号（无版本则为空）")
    private Integer latestVersion;

    @Schema(description = "最新版本创建时间（无版本则为空）")
    private OffsetDateTime latestReleaseAt;

    @Schema(description = "最新草稿更新时间（无草稿则为空）")
    private OffsetDateTime latestDraftAt;

    @Schema(description = "是否存在未发布更改（启发式：latestDraftAt > latestReleaseAt）")
    private Boolean unpublishedChanges;

    @Schema(description = "列表封面 URL（优先最新版本封面，其次草稿封面）")
    private String coverUrl;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}

