package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "用户提交反馈成功响应")
public record UserBugReportCreatedView(
        @Schema(description = "反馈 ID") UUID id,
        @Schema(description = "创建时间") Instant createdAt
) {
}

