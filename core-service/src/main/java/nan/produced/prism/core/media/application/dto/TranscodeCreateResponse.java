package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(name = "TranscodeCreateResponse", description = "创建转码任务响应")
public class TranscodeCreateResponse {

    @Schema(description = "转码任务ID（taskId）", example = "transcode-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
    private String taskId;

    @Schema(description = "消息中心 messageId（用于前端展示进度/结果）")
    private UUID messageId;
}
