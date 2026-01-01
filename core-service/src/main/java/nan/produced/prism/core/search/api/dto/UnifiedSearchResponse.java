package nan.produced.prism.core.search.api.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record UnifiedSearchResponse(
        List<DeviceSearchResult> devices,
        List<ProgramSearchResult> programs,
        List<MediaSearchResult> media
) {
}

