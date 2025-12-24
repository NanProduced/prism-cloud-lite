package nan.produced.prism.core.message.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "未读消息计数")
public record UnreadCountResp(
    @Schema(description = "未读数量")
    long unread
) {
}

