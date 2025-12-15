package nan.produced.prism.core.media.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS S3 配置属性
 *
 * 从环境变量读取凭证：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.media.s3")
public class S3Properties {

    /**
     * AWS 区域
     */
    private String region = "ap-northeast-1";

    /**
     * S3 Bucket 名称
     */
    private String bucket = "prism-media-library";

    /**
     * 自定义端点（用于 MinIO 等 S3 兼容存储，生产环境留空）
     */
    private String endpoint;

    /**
     * 预签名 URL 有效期（分钟）
     */
    private int presignedUrlExpirationMinutes = 15;
}
