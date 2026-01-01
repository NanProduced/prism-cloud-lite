package nan.produced.prism.core.media.application.port.inbound;

/**
 * 全局搜索使用的素材轻量结果项。
 */
public record MediaSearchItem(
        String id,
        String title,
        String kind,
        String mimeType,
        Long sizeBytes,
        String thumbnailUrl
) {
}

