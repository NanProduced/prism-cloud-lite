package nan.produced.prism.core.search.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ProgramSearchResult(
        UUID id,
        String name,
        String resolution,
        OffsetDateTime updatedAt
) {
}

