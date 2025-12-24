package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "BetterUploadResponse", description = "Better Upload 协议响应体（普通上传：单个 PUT 直传）")
public class BetterUploadResponse {

    /**
     * 文件上传信息列表
     */
    @Schema(description = "文件上传信息列表")
    private List<FileUploadInfo> files;

    /**
     * 服务端返回的元数据
     */
    @Schema(description = "服务端回传的元数据（通常为请求中的 metadata）")
    private Map<String, Object> metadata;

    /**
     * 单个文件的上传信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadFileUploadInfo", description = "单个文件的预签名上传信息（普通上传）")
    public static class FileUploadInfo {

        /**
         * 预签名上传 URL
         * 前端使用此 URL 直接 PUT 上传文件到 S3
         */
        @Schema(description = "预签名上传 URL（客户端使用 PUT 直传到对象存储）")
        private String signedUrl;

        /**
         * 文件信息
         */
        @Schema(description = "文件信息（含对象存储 key）")
        private FileDetail file;

        /**
         * 需要附加到上传请求的 HTTP 头
         */
        @Schema(description = "上传时需要附加的 HTTP 头（例如 x-amz-*）")
        private Map<String, String> headers;
    }

    /**
     * 文件详细信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadFileDetail", description = "文件详细信息（含对象存储 ObjectInfo）")
    public static class FileDetail {

        /**
         * 文件名
         */
        @Schema(description = "文件名")
        private String name;

        /**
         * 文件大小（字节）
         */
        @Schema(description = "文件大小（字节）")
        private Long size;

        /**
         * MIME 类型
         */
        @Schema(description = "MIME 类型")
        private String type;

        /**
         * S3 对象信息
         */
        @Schema(description = "对象存储对象信息（key 等）")
        private ObjectInfo objectInfo;
    }

    /**
     * S3 对象信息
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "BetterUploadObjectInfo", description = "对象存储对象信息（key/metadata 等）")
    public static class ObjectInfo {

        /**
         * S3 Object Key
         */
        @Schema(description = "对象 key（后续 batch-finalize 需要传 s3Key）")
        private String key;

        /**
         * 对象元数据（会写入 S3 x-amz-meta-* 头）
         */
        @Schema(description = "对象元数据（会写入 x-amz-meta-* 头）")
        private Map<String, String> metadata;

        /**
         * Cache-Control 头
         */
        @Schema(description = "Cache-Control 头（可选）")
        private String cacheControl;
    }
}
