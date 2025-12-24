package nan.produced.prism.core.device.api.event;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.device.domain.report.program.ProgramPlayTimesReport;

public record DeviceProgramPlayRecordsReportedEvent(
        UUID userId,
        Long deviceId,
        List<ProgramPlayTimesReport> reports,
        String traceId
) {
}

