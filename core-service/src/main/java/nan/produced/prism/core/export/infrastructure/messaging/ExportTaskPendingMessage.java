package nan.produced.prism.core.export.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.export.api.ExportFormat;
import nan.produced.prism.core.export.api.ExportType;

/**
 * 导出任务待执行消息（投递到 core-task-worker-q）。
 */
public record ExportTaskPendingMessage(
        String taskId,
        UUID messageId,
        UUID exportId,
        UUID userId,
        String tier,
        ExportType exportType,
        ExportFormat format,
        String locale,
        String timeZone,
        List<String> fields,
        JsonNode filters
) {
}

