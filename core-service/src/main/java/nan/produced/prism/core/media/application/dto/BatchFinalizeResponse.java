package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 批量落库响应
 *
 * @author Nan
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "BatchFinalizeResponse", description = "批量落库响应")
public class BatchFinalizeResponse {

    /**
     * 创建/返回的素材列表
     */
    @Schema(description = "创建/返回的素材列表")
    private List<AssetResult> assets;

    /**
     * 单个素材结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BatchFinalizeAssetResult", description = "单个素材落库结果")
    public static class AssetResult {

        /**
         * 素材ID
         */
        @Schema(description = "素材ID")
        private String assetId;

        /**
         * 素材组ID（幂等键）
         */
        @Schema(description = "素材组ID（幂等键）")
        private String groupId;

        /**
         * 素材标题
         */
        @Schema(description = "素材标题")
        private String title;

        /**
         * 是否为已存在（幂等返回）
         */
        @Schema(description = "是否为已存在（true 表示幂等返回）")
        private boolean existed;

        /**
         * 素材来源类型：1=上传，2=秒传，3=转码
         */
        @Schema(description = "素材来源类型：1=上传，2=秒传，3=转码", example = "1")
        private Integer sourceType;

        /**
         * 原始文件信息
         */
        @Schema(description = "原始文件信息（必有）")
        private FileResult originalFile;

        /**
         * 封面文件信息
         */
        @Schema(description = "封面文件信息（可选）")
        private FileResult coverFile;

        /**
         * 创建时间
         */
        @Schema(description = "创建时间（ISO-8601）")
        private Instant createdAt;
    }

    /**
     * 文件结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BatchFinalizeFileResult", description = "文件结果（file entity）")
    public static class FileResult {

        /**
         * 文件实体ID
         */
        @Schema(description = "文件实体ID")
        private String fileId;

        /**
         * MIME 类型
         */
        @Schema(description = "MIME 类型")
        private String mimeType;

        /**
         * 文件大小
         */
        @Schema(description = "文件大小（字节）")
        private Long size;

        /**
         * 媒体宽度
         */
        @Schema(description = "媒体宽度（可选）")
        private Integer width;

        /**
         * 媒体高度
         */
        @Schema(description = "媒体高度（可选）")
        private Integer height;

        /**
         * 视频时长（毫秒）
         */
        @Schema(description = "视频时长（毫秒，可选）")
        private Long durationMs;
    }
}
