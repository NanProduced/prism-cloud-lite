package nan.produced.prism.core.feedback.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "反馈附件（截图等）")
public record BugReportAttachment(
        @Schema(description = "可访问的 URL（通常为上传后返回的 CDN/S3 URL）") String url,
        @Schema(description = "文件名（可选）") String name,
        @Schema(description = "MIME 类型（可选）") String mimeType,
        @Schema(description = "大小 bytes（可选）") Long sizeBytes
) {
}

