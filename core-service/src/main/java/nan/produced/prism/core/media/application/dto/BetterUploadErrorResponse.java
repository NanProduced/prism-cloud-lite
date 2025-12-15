package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Better Upload 协议错误响应体
 *
 * 必须严格遵循此格式，否则前端 @better-upload/client 无法解析
 *
 * @see <a href="https://better-upload.com/docs">Better Upload 官方文档</a>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BetterUploadErrorResponse {

    /**
     * 错误信息
     */
    private ErrorInfo error;

    /**
     * 错误详情
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {

        /**
         * 错误类型
         * 必须是以下值之一：
         * - invalid_request: 请求参数不合法
         * - too_many_files: 文件数量超限
         * - file_too_large: 文件大小超限
         * - invalid_file_type: 不支持的文件类型
         * - rejected: 鉴权失败或业务拒绝
         */
        private String type;

        /**
         * 错误描述信息
         */
        private String message;
    }

    // ==================== 工厂方法 ====================

    public static BetterUploadErrorResponse invalidRequest(String message) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("invalid_request")
                        .message(message)
                        .build())
                .build();
    }

    public static BetterUploadErrorResponse tooManyFiles(int maxFiles) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("too_many_files")
                        .message("Maximum " + maxFiles + " files allowed per request")
                        .build())
                .build();
    }

    public static BetterUploadErrorResponse fileTooLarge(String fileName, long maxSize) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("file_too_large")
                        .message("File '" + fileName + "' exceeds maximum size of " + formatSize(maxSize))
                        .build())
                .build();
    }

    public static BetterUploadErrorResponse invalidFileType(String fileName, String type) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("invalid_file_type")
                        .message("File type '" + type + "' is not allowed for file '" + fileName + "'")
                        .build())
                .build();
    }

    public static BetterUploadErrorResponse rejected(String message) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("rejected")
                        .message(message)
                        .build())
                .build();
    }

    public static BetterUploadErrorResponse routeNotFound(String route) {
        return BetterUploadErrorResponse.builder()
                .error(ErrorInfo.builder()
                        .type("invalid_request")
                        .message("Route '" + route + "' not found")
                        .build())
                .build();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
}
