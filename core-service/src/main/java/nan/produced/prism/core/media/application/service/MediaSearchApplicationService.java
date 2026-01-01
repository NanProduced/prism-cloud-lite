package nan.produced.prism.core.media.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.port.inbound.MediaSearchFacade;
import nan.produced.prism.core.media.application.port.inbound.MediaSearchItem;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MediaSearchApplicationService implements MediaSearchFacade {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaObjectUrlPort mediaObjectUrlPort;

    @Override
    @Transactional(readOnly = true)
    public List<MediaSearchItem> searchMedia(UUID userId, String keyword, Integer limit) {
        if (userId == null) {
            return List.of();
        }
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);

        List<String> ids = mediaAssetRepository.listAssetIdsForEditor(
                userId,
                keyword.trim(),
                true, true, true, true,
                safeLimit,
                0
        );
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<MediaAssetEntity> assets = mediaAssetRepository.findWithFilesByIds(ids);
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }

        return assets.stream()
                .map(this::toSearchItem)
                .toList();
    }

    private MediaSearchItem toSearchItem(MediaAssetEntity asset) {
        if (asset == null) {
            return null;
        }

        String mimeType = null;
        Long sizeBytes = null;
        String kind = null;
        String thumbnailUrl = null;

        try {
            if (asset.getOriginalFile() != null) {
                mimeType = asset.getOriginalFile().getMimeType();
                sizeBytes = asset.getOriginalFile().getSize();
            }
            kind = toKind(mimeType);

            String coverKey = asset.getCoverFile() != null ? asset.getCoverFile().getS3Key() : null;
            if (StringUtils.hasText(coverKey)) {
                thumbnailUrl = mediaObjectUrlPort.toPublicUrl(coverKey.trim());
            } else if ("image".equals(kind) && asset.getOriginalFile() != null && StringUtils.hasText(asset.getOriginalFile().getS3Key())) {
                thumbnailUrl = mediaObjectUrlPort.toPublicUrl(asset.getOriginalFile().getS3Key().trim());
            }
        } catch (Exception ignore) {
        }

        return new MediaSearchItem(
                asset.getId(),
                asset.getTitle(),
                kind,
                mimeType,
                sizeBytes,
                thumbnailUrl
        );
    }

    private String toKind(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "other";
        }
        String lower = mimeType.trim().toLowerCase();
        if (lower.startsWith("image/")) {
            return "image";
        }
        if (lower.startsWith("video/")) {
            return "video";
        }
        if (lower.startsWith("application/pdf")
                || lower.startsWith("application/msword")
                || lower.startsWith("application/vnd.")) {
            return "document";
        }
        return "other";
    }
}

