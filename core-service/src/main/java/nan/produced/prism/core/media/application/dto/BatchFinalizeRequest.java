package nan.produced.prism.core.media.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 批量落库请求
 * <p>
 * 上传成功后落库（按 groupId 批量创建素材引用）
 * 支持混合场景：部分秒传 + 部分新上传
 *
 * @author Nan
 */
@Data
public class BatchFinalizeRequest {

    /**
     * 文件夹 ID (null = 用户根目录)
     */
    private String folderId;

    /**
     * 素材列表
     */
    @NotEmpty(message = "items is required")
    @Valid
    private List<AssetItem> items;

    /**
     * 单个素材条目
     */
    @Data
    public static class AssetItem {

        /**
         * 素材组ID（幂等键）
         */
        @NotBlank(message = "groupId is required")
        private String groupId;

        /**
         * 素材标题
         */
        @NotBlank(message = "title is required")
        private String title;

        /**
         * 文件列表（original + cover）
         */
        @NotEmpty(message = "files is required")
        @Valid
        private List<FileItem> files;
    }

    /**
     * 单个文件条目
     */
    @Data
    public static class FileItem {

        /**
         * 文件角色：original 或 cover
         */
        @NotBlank(message = "role is required")
        private String role;

        /**
         * 新上传的 S3 对象路径（与 fileEntityId 二选一）
         */
        private String s3Key;

        /**
         * 秒传时引用的已有文件实体ID（与 s3Key 二选一）
         */
        private String fileEntityId;

        /**
         * MD5 哈希（可选，用于去重）
         */
        private String md5;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "size is required")
        @Positive(message = "size must be positive")
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "type is required")
        private String type;

        /**
         * 原始文件名
         */
        @NotBlank(message = "originalName is required")
        private String originalName;

        /**
         * 媒体宽度（图片/视频）
         */
        private Integer width;

        /**
         * 媒体高度（图片/视频）
         */
        private Integer height;

        /**
         * 视频时长（毫秒）
         */
        private Long durationMs;

        /**
         * 是否为秒传（有 fileEntityId 即为秒传）
         */
        public boolean isInstantUpload() {
            return fileEntityId != null && !fileEntityId.isBlank();
        }

        /**
         * 是否为原始文件
         */
        public boolean isOriginal() {
            return "original".equalsIgnoreCase(role);
        }

        /**
         * 是否为封面文件
         */
        public boolean isCover() {
            return "cover".equalsIgnoreCase(role);
        }
    }
}
