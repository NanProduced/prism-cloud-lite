package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "订阅审计历史分页")
public record UserSubscriptionHistoryView(
    @Schema(description = "事件列表")
    List<UserSubscriptionEventView> items,
    @Schema(description = "页码（从 0 开始）")
    int page,
    @Schema(description = "每页数量")
    int size,
    @Schema(description = "总数")
    long total
) {
}

