package nan.produced.prism.core.media.application.port.outbound;

/**
 * 媒体对象访问 URL 生成端口
 *
 * 用于将 S3 key 转换为可直接访问的预览/下载 URL（例如 CloudFront）。
 */
public interface MediaObjectUrlPort {

    /**
     * 将对象 key 转换为可访问 URL
     *
     * @param objectKey S3 key
     * @return 可访问 URL（若 key 为空返回 null）
     */
    String toPublicUrl(String objectKey);
}

