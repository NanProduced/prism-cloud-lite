package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "BatchFinalizeRequest", description = "批量落库请求（上传完成后创建素材记录）")
public class BatchFinalizeRequest {

    /**
     * 文件夹 ID (null = 用户根目录)
     */
    @Schema(description = "目标文件夹ID（不传/null 表示根目录）")
    private String folderId;

    /**
     * 素材列表
     */
    @NotEmpty(message = "items is required")
    @Valid
    @Schema(description = "素材条目列表（每个条目对应一个素材）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AssetItem> items;

    /**
     * 单个素材条目
     */
    @Data
    @Schema(name = "BatchFinalizeAssetItem", description = "单个素材条目（groupId 幂等）")
    public static class AssetItem {

        /**
         * 素材组ID（幂等键）
         */
        @NotBlank(message = "groupId is required")
        @Schema(description = "素材组ID（幂等键，前端生成；重复请求会返回 existed=true）", example = "group_20251224_0001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String groupId;

        /**
         * 素材标题
         */
        @NotBlank(message = "title is required")
        @Schema(description = "素材标题", example = "宣传片", requiredMode = Schema.RequiredMode.REQUIRED)
        private String title;

        /**
         * 文件列表（original + cover）
         */
        @NotEmpty(message = "files is required")
        @Valid
        @Schema(description = "文件列表（必须包含 role=original；可选 role=cover）", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<FileItem> files;
    }

    /**
     * 单个文件条目
     */
    @Data
    @Schema(name = "BatchFinalizeFileItem", description = "单个文件条目（s3Key/fileEntityId 二选一）")
    public static class FileItem {

        /**
         * 文件角色：original 或 cover
         */
        @NotBlank(message = "role is required")
        @Schema(description = "文件角色：original|cover", allowableValues = { "original", "cover" }, requiredMode = Schema.RequiredMode.REQUIRED)
        private String role;

        /**
         * 新上传的 S3 对象路径（与 fileEntityId 二选一）
         */
        @Schema(description = "新上传的对象存储 key（与 fileEntityId 二选一）", example = "media_library/xxx/demo.mp4")
        private String s3Key;

        /**
         * 秒传时引用的已有文件实体ID（与 s3Key 二选一）
         */
        @Schema(description = "秒传时引用的 fileEntityId（与 s3Key 二选一）")
        private String fileEntityId;

        /**
         * MD5 哈希（可选，用于去重）
         */
        @Schema(description = "MD5（可选）", example = "d41d8cd98f00b204e9800998ecf8427e")
        private String md5;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "size is required")
        @Positive(message = "size must be positive")
        @Schema(description = "文件大小（字节）", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "type is required")
        @Schema(description = "MIME 类型", example = "video/mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;

        /**
         * 原始文件名
         */
        @NotBlank(message = "originalName is required")
        @Schema(description = "原始文件名（用于展示）", example = "demo.mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        private String originalName;

        /**
         * 媒体宽度（图片/视频）
         */
        @Schema(description = "媒体宽度（图片/视频，可选）", example = "1920")
        private Integer width;

        /**
         * 媒体高度（图片/视频）
         */
        @Schema(description = "媒体高度（图片/视频，可选）", example = "1080")
        private Integer height;

        /**
         * 视频时长（毫秒）
         */
        @Schema(description = "视频时长（毫秒，可选）", example = "60000")
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
