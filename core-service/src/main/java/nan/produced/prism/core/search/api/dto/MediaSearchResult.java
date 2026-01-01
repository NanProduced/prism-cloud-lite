package nan.produced.prism.core.search.api.dto;

import lombok.Builder;

@Builder
public record MediaSearchResult(
        String id,
        String title,
        String kind,
        Long size,
        String thumbnailUrl
) {
}

