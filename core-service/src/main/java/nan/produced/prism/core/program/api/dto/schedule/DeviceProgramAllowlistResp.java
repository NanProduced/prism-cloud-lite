package nan.produced.prism.core.program.api.dto.schedule;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeviceProgramAllowlistResp {

    private Integer releaseProgramId;

    private UUID programId;

    private String programName;

    private Integer releaseVersion;

    private String deviceTitleSnapshot;

    /**
     * direct-publish / schedule / both
     */
    private String source;

    private UUID scheduleId;

    private OffsetDateTime assignedAt;

    private String deploymentStatus;
}

