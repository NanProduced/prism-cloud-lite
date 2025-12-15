package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class BatchFinalizeResponse {

    /**
     * 创建/返回的素材列表
     */
    private List<AssetResult> assets;

    /**
     * 单个素材结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AssetResult {

        /**
         * 素材ID
         */
        private String assetId;

        /**
         * 素材组ID（幂等键）
         */
        private String groupId;

        /**
         * 素材标题
         */
        private String title;

        /**
         * 是否为已存在（幂等返回）
         */
        private boolean existed;

        /**
         * 素材来源类型：1=上传，2=秒传，3=转码
         */
        private Integer sourceType;

        /**
         * 原始文件信息
         */
        private FileResult originalFile;

        /**
         * 封面文件信息
         */
        private FileResult coverFile;

        /**
         * 创建时间
         */
        private Instant createdAt;
    }

    /**
     * 文件结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileResult {

        /**
         * 文件实体ID
         */
        private String fileId;

        /**
         * MIME 类型
         */
        private String mimeType;

        /**
         * 文件大小
         */
        private Long size;

        /**
         * 媒体宽度
         */
        private Integer width;

        /**
         * 媒体高度
         */
        private Integer height;

        /**
         * 视频时长（毫秒）
         */
        private Long durationMs;
    }
}
