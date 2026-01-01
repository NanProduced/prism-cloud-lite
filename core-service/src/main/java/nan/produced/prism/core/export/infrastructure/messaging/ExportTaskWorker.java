package nan.produced.prism.core.export.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.export.application.service.ExportWorkerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskWorker {

    private final ExportWorkerService exportWorkerService;

    @RabbitListener(
            queues = MessagingConstants.Queues.EXPORT_WORKER,
            concurrency = "${prism.export.worker-concurrency:1}"
    )
    public void onTask(ExportTaskPendingMessage message) {
        if (message == null) {
            return;
        }
        exportWorkerService.handle(message);
    }
}

