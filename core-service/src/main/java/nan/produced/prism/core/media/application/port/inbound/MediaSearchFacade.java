package nan.produced.prism.core.media.application.port.inbound;

import java.util.List;
import java.util.UUID;

/**
 * 全局搜索使用：素材快速检索能力（跨模块公开 API）。
 */
public interface MediaSearchFacade {

    List<MediaSearchItem> searchMedia(UUID userId, String keyword, Integer limit);
}

