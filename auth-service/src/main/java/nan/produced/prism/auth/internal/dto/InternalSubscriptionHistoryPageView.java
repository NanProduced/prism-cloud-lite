package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "InternalSubscriptionHistoryPageView", description = "订阅审计分页（内部接口）")
public record InternalSubscriptionHistoryPageView(
    @Schema(description = "事件列表") List<InternalSubscriptionEventView> items,
    @Schema(description = "页码（从 0 开始）") int page,
    @Schema(description = "每页数量") int size,
    @Schema(description = "总数") long total
) {
}

