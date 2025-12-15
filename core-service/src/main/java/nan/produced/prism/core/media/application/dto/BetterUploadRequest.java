package nan.produced.prism.core.media.application.dto;

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
public class BetterUploadRequest {

    /**
     * 上传路由名称
     * 用于选择不同的上传配置（文件类型、大小限制等）
     */
    @NotBlank(message = "route is required")
    private String route;

    /**
     * 待上传文件列表
     */
    @NotEmpty(message = "files is required")
    @Valid
    private List<FileInfo> files;

    /**
     * 客户端元数据
     * 包含业务相关信息（如 folderId、groupId、role 等）
     */
    private Map<String, Object> metadata;

    /**
     * 文件基本信息
     */
    @Data
    public static class FileInfo {

        /**
         * 文件名
         */
        @NotBlank(message = "file name is required")
        private String name;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "file size is required")
        @Positive(message = "file size must be positive")
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "file type is required")
        private String type;
    }
}
