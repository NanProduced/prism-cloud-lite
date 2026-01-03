package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "管理端-Manager 分页")
public record AdminManagerPageView(
    @Schema(description = "分页列表")
    List<AdminManagerItemView> items,
    @Schema(description = "页码（0-based）")
    int page,
    @Schema(description = "每页大小")
    int size,
    @Schema(description = "总条数")
    long total
) {
}

