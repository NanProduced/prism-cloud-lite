package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Better Upload 协议响应体（非 Multipart）
 *
 * 注意：此响应不包裹在 BffResponse 中，直接返回原始 JSON
 *
 * @see <a href="https://better-upload.com/docs">Better Upload 官方文档</a>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BetterUploadResponse {

    /**
     * 文件上传信息列表
     */
    private List<FileUploadInfo> files;

    /**
     * 服务端返回的元数据
     */
    private Map<String, Object> metadata;

    /**
     * 单个文件的上传信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileUploadInfo {

        /**
         * 预签名上传 URL
         * 前端使用此 URL 直接 PUT 上传文件到 S3
         */
        private String signedUrl;

        /**
         * 文件信息
         */
        private FileDetail file;

        /**
         * 需要附加到上传请求的 HTTP 头
         */
        private Map<String, String> headers;
    }

    /**
     * 文件详细信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileDetail {

        /**
         * 文件名
         */
        private String name;

        /**
         * 文件大小（字节）
         */
        private Long size;

        /**
         * MIME 类型
         */
        private String type;

        /**
         * S3 对象信息
         */
        private ObjectInfo objectInfo;
    }

    /**
     * S3 对象信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ObjectInfo {

        /**
         * S3 Object Key
         */
        private String key;

        /**
         * 对象元数据（会写入 S3 x-amz-meta-* 头）
         */
        private Map<String, String> metadata;

        /**
         * Cache-Control 头
         */
        private String cacheControl;
    }
}
