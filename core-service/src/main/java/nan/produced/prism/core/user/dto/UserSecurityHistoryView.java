package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "账号安全审计历史分页结果（Security History）")
public record UserSecurityHistoryView(
    @Schema(description = "事件列表（按时间倒序）")
    List<UserSecurityEventView> items,
    @Schema(description = "页码（从 0 开始）")
    int page,
    @Schema(description = "每页大小")
    int size,
    @Schema(description = "总条数")
    long total
) {
}

