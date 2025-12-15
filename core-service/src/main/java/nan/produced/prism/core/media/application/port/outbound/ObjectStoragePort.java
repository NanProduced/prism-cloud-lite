package nan.produced.prism.core.media.application.port.outbound;

import java.time.Duration;
import java.util.Map;

/**
 * 对象存储出站端口
 * <p>
 * 抽象 S3 操作，便于测试和未来替换存储后端
 */
public interface ObjectStoragePort {

    /**
     * 生成预签名 PUT URL（用于普通上传）
     *
     * @param key          S3 对象 Key
     * @param contentType  文件 MIME 类型
     * @param metadata     自定义元数据
     * @param expiration   URL 有效期
     * @return 预签名 URL
     */
    String generatePresignedPutUrl(String key, String contentType, Map<String, String> metadata, Duration expiration);

    /**
     * 创建 Multipart 上传
     *
     * @param key         S3 对象 Key
     * @param contentType 文件 MIME 类型
     * @param metadata    自定义元数据
     * @return Multipart Upload ID
     */
    String createMultipartUpload(String key, String contentType, Map<String, String> metadata);

    /**
     * 生成分片上传预签名 URL
     *
     * @param key        S3 对象 Key
     * @param uploadId   Multipart Upload ID
     * @param partNumber 分片编号（从 1 开始）
     * @param expiration URL 有效期
     * @return 预签名 URL
     */
    String generatePresignedPartUrl(String key, String uploadId, int partNumber, Duration expiration);

    /**
     * 生成完成 Multipart 上传的预签名 URL
     *
     * @param key        S3 对象 Key
     * @param uploadId   Multipart Upload ID
     * @param expiration URL 有效期
     * @return 预签名 URL
     */
    String generatePresignedCompleteUrl(String key, String uploadId, Duration expiration);

    /**
     * 生成取消 Multipart 上传的预签名 URL
     *
     * @param key        S3 对象 Key
     * @param uploadId   Multipart Upload ID
     * @param expiration URL 有效期
     * @return 预签名 URL
     */
    String generatePresignedAbortUrl(String key, String uploadId, Duration expiration);

    /**
     * 检查对象是否存在
     *
     * @param key S3 对象 Key
     * @return 是否存在
     */
    boolean objectExists(String key);
}
