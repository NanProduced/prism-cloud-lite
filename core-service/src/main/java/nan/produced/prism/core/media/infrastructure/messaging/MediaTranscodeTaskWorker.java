package nan.produced.prism.core.media.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.media.application.service.MediaTranscodeWorkerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaTranscodeTaskWorker {

    private final MediaTranscodeWorkerService mediaTranscodeWorkerService;

    @RabbitListener(
        queues = MessagingConstants.Queues.TASK_WORKER,
        concurrency = "${prism.media.transcode.worker-concurrency:1}",
        containerFactory = "taskRabbitListenerContainerFactory"
    )
    public void onTask(TranscodeTaskPendingMessage message) {
        if (message == null) {
            return;
        }
        mediaTranscodeWorkerService.handle(message);
    }
}
