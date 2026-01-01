package nan.produced.prism.core.export.api.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record CreateExportResponse(
        String taskId,
        UUID messageId,
        UUID exportId
) {
}

