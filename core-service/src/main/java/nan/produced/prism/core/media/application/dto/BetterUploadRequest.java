package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Better Upload 协议请求体
 *
 * @see <a href="https://better-upload.com/docs">Better Upload 官方文档</a>
 */
@Data
@Schema(name = "BetterUploadRequest", description = "Better Upload 协议请求体（用于获取预签名上传 URL）")
public class BetterUploadRequest {

    /**
     * 上传路由名称
     * 用于选择不同的上传配置（文件类型、大小限制等）
     */
    @NotBlank(message = "route is required")
    @Schema(description = "上传路由名称（决定允许的 MIME、大小、是否启用 multipart）", example = "mediaLibrary", requiredMode = Schema.RequiredMode.REQUIRED)
    private String route;

    /**
     * 待上传文件列表
     */
    @NotEmpty(message = "files is required")
    @Valid
    @Schema(description = "待上传文件列表（仅包含元信息，不包含文件内容）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FileInfo> files;

    /**
     * 客户端元数据
     * 包含业务相关信息（如 folderId、groupId、role 等）
     */
    @Schema(description = "客户端元数据（可选，会在响应中原样回传，便于前端透传业务上下文）")
    private Map<String, Object> metadata;

    /**
     * 文件基本信息
     */
    @Data
    @Schema(name = "BetterUploadFileInfo", description = "单个文件元信息（不含文件内容）")
    public static class FileInfo {

        /**
         * 文件名
         */
        @NotBlank(message = "file name is required")
        @Schema(description = "文件名", example = "demo.mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "file size is required")
        @Positive(message = "file size must be positive")
        @Schema(description = "文件大小（字节）", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "file type is required")
        @Schema(description = "MIME 类型", example = "video/mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;
    }
}
