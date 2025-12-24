package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Better Upload 协议 Multipart 响应体
 *
 * 当文件大小超过阈值时返回此响应，支持分片上传
 *
 * @see <a href="https://better-upload.com/docs">Better Upload 官方文档</a>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "BetterUploadMultipartResponse", description = "Better Upload 协议响应体（Multipart：分片上传）")
public class BetterUploadMultipartResponse {

    /**
     * Multipart 上传信息
     */
    @Schema(description = "Multipart 上传信息")
    private MultipartInfo multipart;

    /**
     * 服务端返回的元数据
     */
    @Schema(description = "服务端回传的元数据（通常为请求中的 metadata）")
    private Map<String, Object> metadata;

    /**
     * Multipart 上传详细信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadMultipartInfo", description = "Multipart 上传信息（含 parts 列表与 partSize）")
    public static class MultipartInfo {

        /**
         * 文件分片上传信息列表
         */
        @Schema(description = "文件分片上传信息列表")
        private List<FileMultipartInfo> files;

        /**
         * 分片大小（字节）
         */
        @Schema(description = "分片大小（字节）")
        private Long partSize;
    }

    /**
     * 单个文件的 Multipart 上传信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadFileMultipartInfo", description = "单个文件的 Multipart 上传信息")
    public static class FileMultipartInfo {

        /**
         * 文件信息
         */
        @Schema(description = "文件信息（含对象存储 key）")
        private BetterUploadResponse.FileDetail file;

        /**
         * 分片上传信息列表
         */
        @Schema(description = "分片上传信息列表（每个 partNumber 对应一个 signedUrl）")
        private List<PartInfo> parts;

        /**
         * S3 Multipart Upload ID
         */
        @Schema(description = "Multipart uploadId（完成/取消上传会用到）")
        private String uploadId;

        /**
         * 完成上传的预签名 URL
         * 前端上传完所有分片后，POST 此 URL 完成合并
         */
        @Schema(description = "完成上传的预签名 URL（上传完所有分片后调用）")
        private String completeSignedUrl;

        /**
         * 取消上传的预签名 URL
         * 上传失败时，DELETE 此 URL 清理已上传的分片
         */
        @Schema(description = "取消上传的预签名 URL（上传失败时清理已上传分片）")
        private String abortSignedUrl;
    }

    /**
     * 分片信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadPartInfo", description = "单个分片上传信息")
    public static class PartInfo {

        /**
         * 分片预签名上传 URL
         */
        @Schema(description = "分片上传 signedUrl（PUT 直传）")
        private String signedUrl;

        /**
         * 分片编号（从 1 开始）
         */
        @Schema(description = "分片编号（从 1 开始）")
        private Integer partNumber;

        /**
         * 分片大小（字节）
         */
        @Schema(description = "分片大小（字节）")
        private Long size;
    }
}
