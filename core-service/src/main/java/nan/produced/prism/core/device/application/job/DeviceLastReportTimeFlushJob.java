package nan.produced.prism.core.device.application.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.application.service.DeviceLastReportTimeWriteBehindBuffer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceLastReportTimeFlushJob {

    private final DeviceLastReportTimeWriteBehindBuffer buffer;

    @Scheduled(
            fixedDelayString = "${prism.core.device.last-report-time.flush-delay-ms:60000}",
            initialDelayString = "${prism.core.device.last-report-time.initial-delay-ms:60000}"
    )
    public void flushLastReportTime() {
        int buffered = buffer.bufferedSize();
        if (buffered <= 0) {
            return;
        }
        int updated = buffer.flushOnce();
        log.debug("DeviceLastReportTimeFlushJob - flushed lastReportTime: updated={}, bufferedBefore={}", updated, buffered);
    }
}

