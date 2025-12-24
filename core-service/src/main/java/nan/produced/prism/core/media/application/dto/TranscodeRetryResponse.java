package nan.produced.prism.core.media.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TranscodeRetryResponse {

    private String taskId;

    private UUID messageId;
}

