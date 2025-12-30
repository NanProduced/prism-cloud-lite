package nan.produced.prism.core.message.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;

@Schema(description = "消息中心列表项")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageListItemResp {

    @Schema(description = "消息ID")
    private UUID id;

    @Schema(description = "消息类别（通知/任务）")
    private MessageKind kind;

    @Schema(description = "消息类型（用于前端渲染与筛选）")
    private String type;

    @Schema(description = "消息状态")
    private MessageStatus status;

    @Schema(description = "渲染载荷（JSON），前端按 type/status 自行 i18n 渲染")
    private JsonNode payload;

    @Schema(description = "关联设备ID（可为空）")
    private Long deviceId;

    @Schema(description = "关联设备名称（冗余字段，便于前端展示，可为空）")
    private String deviceName;

    @Schema(description = "关联节目ID（可为空）")
    private UUID programId;

    @Schema(description = "关联节目名称（冗余字段，便于前端展示，可为空）")
    private String programName;

    @Schema(description = "关联操作ID（指令/批量/发布等，可为空）")
    private String operationId;

    @Schema(description = "关联任务ID（可为空）")
    private String taskId;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "已读时间（null=未读）")
    private OffsetDateTime readAt;
}
