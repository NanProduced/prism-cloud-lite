package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "内部接口-管理账号分页响应")
public class InternalAdminUserPageView {
    @Schema(description = "分页列表")
    List<InternalAdminUserItem> items;

    @Schema(description = "页码（0-based）")
    int page;

    @Schema(description = "每页大小")
    int size;

    @Schema(description = "总条数")
    long total;
}

