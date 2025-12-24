package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(name = "TranscodeRetryResponse", description = "转码重试响应")
public class TranscodeRetryResponse {

    @Schema(description = "转码任务ID（taskId）")
    private String taskId;

    @Schema(description = "消息中心 messageId（用于前端展示进度/结果）")
    private UUID messageId;
}
