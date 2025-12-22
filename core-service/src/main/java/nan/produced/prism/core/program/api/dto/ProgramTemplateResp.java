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
@Schema(description = "节目模板响应")
public class ProgramTemplateResp {

    @Schema(description = "模板ID")
    private UUID templateId;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "画布宽度")
    private Integer width;

    @Schema(description = "画布高度")
    private Integer height;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}

