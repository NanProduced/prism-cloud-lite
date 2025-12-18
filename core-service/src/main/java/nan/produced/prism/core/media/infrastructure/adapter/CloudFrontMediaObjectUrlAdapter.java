package nan.produced.prism.core.media.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.media.infrastructure.config.MediaDeliveryProperties;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CloudFront/S3 访问 URL 生成适配器
 */
@Component
@RequiredArgsConstructor
public class CloudFrontMediaObjectUrlAdapter implements MediaObjectUrlPort {

    private final MediaDeliveryProperties mediaDeliveryProperties;
    private final S3Properties s3Properties;

    @Override
    public String toPublicUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }

        var cdnBaseUrl = normalizeBaseUrl(mediaDeliveryProperties.getBaseUrl());
        if (StringUtils.hasText(cdnBaseUrl)) {
            return joinUrl(cdnBaseUrl, objectKey);
        }

        var endpoint = normalizeBaseUrl(s3Properties.getEndpoint());
        if (StringUtils.hasText(endpoint)) {
            return joinUrl(joinUrl(endpoint, s3Properties.getBucket()), objectKey);
        }

        // AWS S3 virtual-hosted-style（仅在 Bucket 公开时可直接访问）
        var s3Base = String.format("https://%s.s3.%s.amazonaws.com", s3Properties.getBucket(), s3Properties.getRegion());
        return joinUrl(s3Base, objectKey);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private String joinUrl(String base, String path) {
        if (!StringUtils.hasText(base)) {
            return path;
        }
        var normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return base + "/" + normalizedPath;
    }
}

