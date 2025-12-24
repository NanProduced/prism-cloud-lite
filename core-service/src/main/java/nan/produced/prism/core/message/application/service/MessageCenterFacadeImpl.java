package nan.produced.prism.core.message.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageCenterFacadeImpl implements MessageCenterFacade {

    private static final String MESSAGE_TYPE_DEVICE_COMMAND_FINISHED = "device.command.finished";

    private final BatchCommandMessageTracker batchCommandMessageTracker;
    private final ProgramPublishMessageTracker programPublishMessageTracker;
    private final MessageWriteApplicationService messageWriteApplicationService;

    @Override
    public UUID createTaskMessage(UUID userId,
                                 String type,
                                 String title,
                                 String summary,
                                 Object payload,
                                 String taskId) {
        return messageWriteApplicationService.createAndPublish(
            MessageKind.TASK,
            type,
            MessageStatus.PENDING,
            userId,
            title,
            summary,
            payload,
            null,
            null,
            null,
            taskId
        ).getId();
    }

    @Override
    public void updateTaskMessage(UUID userId,
                                  UUID messageId,
                                  String status,
                                  String title,
                                  String summary,
                                  Object payload) {
        MessageStatus parsed = parseStatus(status);
        messageWriteApplicationService.updateAndPublish(userId, messageId, parsed, title, summary, payload);
    }

    @Override
    public void startBatchCommandTracking(UUID userId, String batchOperationId, Collection<BatchCommandItem> items) {
        batchCommandMessageTracker.start(userId, batchOperationId, items);
    }

    @Override
    public boolean onBatchCommandFinalState(String commandId, UUID userId, boolean success, boolean expired) {
        return batchCommandMessageTracker.onCommandFinalState(commandId, userId, success, expired);
    }

    @Override
    public String startProgramPublishTracking(UUID userId,
                                             UUID programId,
                                             String programName,
                                             int version,
                                             int releaseProgramId,
                                             Collection<Long> targetDeviceIds,
                                             Collection<Long> onlineDeviceIdsAtPublishTime) {
        return programPublishMessageTracker.start(
            userId,
            programId,
            programName,
            version,
            releaseProgramId,
            targetDeviceIds,
            onlineDeviceIdsAtPublishTime);
    }

    @Override
    public void onProgramDeviceDownloaded(UUID userId, Long deviceId, Integer releaseProgramId) {
        programPublishMessageTracker.onDeviceDownloaded(userId, deviceId, releaseProgramId);
    }

    @Override
    public void publishDeviceCommandFinished(DeviceCommandFinishedMessage message) {
        if (message == null || message.userId() == null || message.deviceId() == null) {
            return;
        }

        String actionType = StringUtils.hasText(message.actionType()) ? message.actionType().trim() : "UNKNOWN";
        String finalStatus = StringUtils.hasText(message.finalStatus()) ? message.finalStatus().trim() : null;
        boolean expired = "EXPIRED".equals(finalStatus);

        MessageStatus status = expired ? MessageStatus.FAILED : MessageStatus.SUCCESS;
        String title = expired ? "设备指令执行超时" : "设备指令已完成";
        String summary = expired
            ? String.format("设备 %d 的指令 %s 已过期", message.deviceId(), actionType)
            : String.format("设备 %d 的指令 %s 已完成", message.deviceId(), actionType);

        Map<String, Object> payload = new HashMap<>();
        payload.put("operationType", "DEVICE_COMMAND");
        payload.put("operationId", message.operationId());
        payload.put("deviceId", message.deviceId());
        payload.put("actionType", actionType);
        if (StringUtils.hasText(message.trackingLevel())) {
            payload.put("trackingLevel", message.trackingLevel());
        }
        payload.put("finalStatus", finalStatus);
        payload.put("accepted", message.accepted());
        payload.put("covered", message.covered());
        if (StringUtils.hasText(message.sendMethod())) {
            payload.put("sendMethod", message.sendMethod());
        }
        if (message.queuedId() != null) {
            payload.put("queuedId", message.queuedId());
        }
        if (StringUtils.hasText(message.errorMessage())) {
            payload.put("errorMessage", message.errorMessage());
        }

        messageWriteApplicationService.createAndPublish(
            MessageKind.NOTIFICATION,
            MESSAGE_TYPE_DEVICE_COMMAND_FINISHED,
            status,
            message.userId(),
            title,
            summary,
            payload,
            message.deviceId(),
            null,
            message.operationId(),
            null
        );
    }

    private MessageStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new IllegalArgumentException("status is required");
        }
        return MessageStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    }
}
