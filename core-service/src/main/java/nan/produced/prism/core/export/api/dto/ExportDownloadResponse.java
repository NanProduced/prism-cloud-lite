package nan.produced.prism.core.export.api.dto;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ExportDownloadResponse(
        String url,
        OffsetDateTime expiresAt
) {
}

