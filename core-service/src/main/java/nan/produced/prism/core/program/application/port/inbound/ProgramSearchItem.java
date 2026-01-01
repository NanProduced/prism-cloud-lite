package nan.produced.prism.core.program.application.port.inbound;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 全局搜索使用的节目轻量结果项。
 */
public record ProgramSearchItem(
        UUID id,
        String name,
        String resolution,
        OffsetDateTime updatedAt
) {
}

