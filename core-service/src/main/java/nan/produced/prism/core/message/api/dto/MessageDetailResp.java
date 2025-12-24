package nan.produced.prism.core.message.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;

@Schema(description = "消息详情")
public record MessageDetailResp(
    @Schema(description = "消息ID") UUID id,
    @Schema(description = "消息类别") MessageKind kind,
    @Schema(description = "消息类型") String type,
    @Schema(description = "状态") MessageStatus status,
    @Schema(description = "标题") String title,
    @Schema(description = "摘要") String summary,
    @Schema(description = "详情载荷（JSON）") JsonNode payload,
    @Schema(description = "关联设备ID") Long deviceId,
    @Schema(description = "关联节目ID") UUID programId,
    @Schema(description = "关联操作ID") String operationId,
    @Schema(description = "关联任务ID") String taskId,
    @Schema(description = "已读时间（null=未读）") OffsetDateTime readAt,
    @Schema(description = "创建时间") OffsetDateTime createdAt,
    @Schema(description = "更新时间") OffsetDateTime updatedAt
) {
}

