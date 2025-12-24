package nan.produced.prism.core.message.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "标记已读结果")
public record MarkReadResp(
    @Schema(description = "实际更新条数（仅统计从未读→已读）")
    int updated
) {
}

