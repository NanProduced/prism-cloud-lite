package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class BetterUploadMultipartResponse {

    /**
     * Multipart 上传信息
     */
    private MultipartInfo multipart;

    /**
     * 服务端返回的元数据
     */
    private Map<String, Object> metadata;

    /**
     * Multipart 上传详细信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MultipartInfo {

        /**
         * 文件分片上传信息列表
         */
        private List<FileMultipartInfo> files;

        /**
         * 分片大小（字节）
         */
        private Long partSize;
    }

    /**
     * 单个文件的 Multipart 上传信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileMultipartInfo {

        /**
         * 文件信息
         */
        private BetterUploadResponse.FileDetail file;

        /**
         * 分片上传信息列表
         */
        private List<PartInfo> parts;

        /**
         * S3 Multipart Upload ID
         */
        private String uploadId;

        /**
         * 完成上传的预签名 URL
         * 前端上传完所有分片后，POST 此 URL 完成合并
         */
        private String completeSignedUrl;

        /**
         * 取消上传的预签名 URL
         * 上传失败时，DELETE 此 URL 清理已上传的分片
         */
        private String abortSignedUrl;
    }

    /**
     * 分片信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartInfo {

        /**
         * 分片预签名上传 URL
         */
        private String signedUrl;

        /**
         * 分片编号（从 1 开始）
         */
        private Integer partNumber;

        /**
         * 分片大小（字节）
         */
        private Long size;
    }
}
