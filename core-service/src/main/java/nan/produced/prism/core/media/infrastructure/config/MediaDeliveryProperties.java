package nan.produced.prism.core.media.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 媒体资源投递配置（如 CloudFront CDN）
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.media.delivery")
public class MediaDeliveryProperties {

    /**
     * 对外访问的 CDN Base URL，例如：https://dxxxxx.cloudfront.net
     */
    private String baseUrl;
}

