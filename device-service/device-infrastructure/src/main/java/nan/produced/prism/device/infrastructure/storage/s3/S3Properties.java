package nan.produced.prism.device.infrastructure.storage.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS S3 配置属性（用于设备截图上传）
 *
 * 凭证从环境变量读取：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.media.s3")
public class S3Properties {

    /**
     * AWS 区域
     * 默认香港
     */
    private String region = "ap-east-1";

    /**
     * S3 Bucket 名称
     */
    private String bucket = "prism-cloud-lite-asset-s3-hk";

    /**
     * 自定义端点（用于 MinIO 等 S3 兼容存储）
     */
    private String endpoint;
}

