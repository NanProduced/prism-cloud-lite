package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
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
@Schema(description = "节目详情")
public class ProgramDetailResp {

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

    @Schema(description = "草稿列表（baseVersion 维度）")
    private List<ProgramDraftResp> drafts;

    @Schema(description = "发布版本列表（按 version 倒序）")
    private List<ProgramVersionResp> versions;

    @Schema(description = "部署关系列表（按 assignedAt 倒序）")
    private List<ProgramDeploymentResp> deployments;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}

