package nan.produced.prism.core.media.infrastructure.config;

import nan.produced.prism.core.common.config.StoragePathProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 配置
 *
 * 凭证从环境变量读取：
 * - AWS_ACCESS_KEY_ID
 * - AWS_SECRET_ACCESS_KEY
 */
@Configuration
@EnableConfigurationProperties({
        S3Properties.class,
        UploadRouteProperties.class,
        MediaDeliveryProperties.class,
        TranscodeProperties.class,
        StoragePathProperties.class})
public class S3Configuration {

    @Bean
    public S3Client s3Client(S3Properties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create());

        // 支持自定义端点（用于 S3 兼容存储）
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            // 兼容 MinIO 等服务需要 path-style 访问
            builder.forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create());

        // 支持自定义端点
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }
}
