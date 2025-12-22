package nan.produced.prism.core.common.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 设备下载进度上报用例（由 device 模块调用，program 模块处理落库）。
 */
public interface ProgramDownloadProgressUseCase {

    void handleDownloadingProgress(Long deviceId, UUID userId, String reportData, Instant occurredAt, String traceId);
}
