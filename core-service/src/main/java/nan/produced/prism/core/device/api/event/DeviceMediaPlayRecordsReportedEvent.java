package nan.produced.prism.core.device.api.event;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.device.domain.report.media.MediaPlayTimesReport;

public record DeviceMediaPlayRecordsReportedEvent(
        UUID userId,
        Long deviceId,
        List<MediaPlayTimesReport> reports,
        String traceId
) {
}

