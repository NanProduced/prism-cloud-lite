package nan.produced.prism.core.message.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "消息中心分页结果（按创建时间倒序）")
public record MessagePageResp(
    @Schema(description = "消息列表")
    List<MessageListItemResp> items,
    @Schema(description = "页码（从 0 开始）")
    int page,
    @Schema(description = "每页大小")
    int size,
    @Schema(description = "总条数")
    long total
) {
}

