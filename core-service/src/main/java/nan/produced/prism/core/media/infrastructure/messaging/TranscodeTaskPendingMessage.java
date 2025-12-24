package nan.produced.prism.core.media.infrastructure.messaging;

import java.util.UUID;
import nan.produced.prism.core.media.application.dto.TranscodeOptions;

/**
 * 转码任务待执行消息（投递到 core-task-worker-q）
 */
public record TranscodeTaskPendingMessage(
        String taskId,
        UUID messageId,
        UUID userId,
        String tier,
        String assetId,
        String presetId,
        String targetFolderId,
        TranscodeOptions options
) {
}
