package nan.produced.prism.core.message.api;

import java.util.Collection;
import java.util.UUID;

/**
 * 消息中心对外（跨模块）使用的门面接口。
 *
 * <p>说明：为满足 Spring Modulith 的模块边界校验，其他模块只能依赖 message.api 暴露的类型。</p>
 */
public interface MessageCenterFacade {

    record BatchCommandItem(String commandId, Long deviceId, String actionType, boolean accepted) {
    }

    record DeviceCommandFinishedMessage(
        UUID userId,
        Long deviceId,
        String operationId,
        String actionType,
        String trackingLevel,
        String finalStatus,
        boolean accepted,
        boolean covered,
        String sendMethod,
        Integer queuedId,
        String errorMessage
    ) {
    }

    /**
     * 创建任务类消息（消息中心 + SSE）。
     *
     * <p>说明：用于跨模块异步任务（如转码/导出等）在消息中心展示进度与结果。</p>
     *
     * @return messageId
     */
    UUID createTaskMessage(UUID userId,
                           String type,
                           String title,
                           String summary,
                           Object payload,
                           String taskId);

    /**
     * 更新任务类消息（消息中心 + SSE）。
     *
     * <p>status 取值：PENDING/RUNNING/SUCCESS/FAILED</p>
     */
    void updateTaskMessage(UUID userId,
                           UUID messageId,
                           String status,
                           String title,
                           String summary,
                           Object payload);

    void startBatchCommandTracking(UUID userId, String batchOperationId, Collection<BatchCommandItem> items);

    /**
     * @return true 表示该指令属于批量操作；false 表示不属于批量
     */
    boolean onBatchCommandFinalState(String commandId, UUID userId, boolean success, boolean expired);

    String startProgramPublishTracking(UUID userId,
                                      UUID programId,
                                      String programName,
                                      int version,
                                      int releaseProgramId,
                                      Collection<Long> targetDeviceIds,
                                      Collection<Long> onlineDeviceIdsAtPublishTime);

    void onProgramDeviceDownloaded(UUID userId, Long deviceId, Integer releaseProgramId);

    void publishDeviceCommandFinished(DeviceCommandFinishedMessage message);
}
