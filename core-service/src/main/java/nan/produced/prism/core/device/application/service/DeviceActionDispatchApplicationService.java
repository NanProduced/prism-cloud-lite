package nan.produced.prism.core.device.application.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.IdGenerator;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchItemReq;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchReq;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.device.application.converter.DeviceActionDispatchConverter;
import nan.produced.prism.core.device.application.port.inbound.DeviceActionDispatchUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandLogRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceActionDispatchApplicationService implements DeviceActionDispatchUseCase {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandLogRepository deviceCommandLogRepository;
    private final DeviceInternalClient deviceInternalClient;
    private final DeviceActionDispatchConverter deviceActionDispatchConverter;
    private final MessageCenterFacade messageCenterFacade;
    private final RabbitMessagePublisher rabbitMessagePublisher;

    private static final String SSE_TYPE_OPERATION_UPDATED = "operation.updated";

    private static final int MAX_BATCH_OPERATION_SSE = 50;

    /**
     * 单个设备操作下发
     * @param userId 用户Id
     * @param deviceId 设备Id
     * @param action 设备操作
     * @return 设备操作结果
     */
    @Override
    public DeviceActionDispatchResp dispatchSingle(UUID userId, Long deviceId, DeviceActionBase action) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE, "deviceId is null");
        }
        if (action == null || action.getType() == null) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "action/type is null");
        }

        ensureUserOwnsDevice(userId, deviceId);

        String commandId = UUID.randomUUID().toString();
        DeviceCommandReq commandReq = deviceActionDispatchConverter.toDeviceCommandReq(deviceId, commandId, action);
        // 调用 device-service
        DeviceCommandResp commandResp = callDeviceService(List.of(commandReq));
        DeviceCommandResp.CommandResult result = findResult(commandResp, commandId);

        DeviceCommandLog log = buildDeviceCommandLog(userId, deviceId, commandId, action, result, commandReq.getTtlMinutes());
        // 保存日志记录
        deviceCommandLogRepository.save(log);

        publishOperationUpdatedBestEffort(log);

        return deviceActionDispatchConverter.toDispatchResp(deviceId, commandId, action, result);
    }

    /**
     * 批量设备操作下发
     * @param userId 用户Id
     * @param req 设备操作列表
     * @return 设备操作结果列表
     */
    @Override
    public BatchDeviceActionDispatchResp dispatchBatch(UUID userId, BatchDeviceActionDispatchReq req) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "items is empty");
        }

        List<BatchDeviceActionDispatchItemReq> items = req.getItems();

        String batchOperationId = UUID.randomUUID().toString();

        List<PendingDispatch> pending = items.stream().map(item -> {
            if (item == null || item.getDeviceId() == null) {
                throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "deviceId is null");
            }
            if (item.getAction() == null || item.getAction().getType() == null) {
                throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "action/type is null");
            }
            ensureUserOwnsDevice(userId, item.getDeviceId());
            String commandId = UUID.randomUUID().toString();
            return new PendingDispatch(item.getDeviceId(), commandId, item.getAction());
        }).toList();

        // 先写入 Redis 聚合索引，避免极端情况下 confirm/expired 回执早于本次 HTTP 请求落库导致批量聚合丢失。
        messageCenterFacade.startBatchCommandTracking(
            userId,
            batchOperationId,
            pending.stream()
                .map(p -> new MessageCenterFacade.BatchCommandItem(
                    p.commandId(),
                    p.deviceId(),
                    p.action() != null && p.action().getType() != null ? p.action().getType().name() : null,
                    true))
                .toList());

        List<DeviceCommandReq> commands = pending.stream()
                .map(pendingDispatch -> deviceActionDispatchConverter.toDeviceCommandReq(
                        pendingDispatch.deviceId(),
                        pendingDispatch.commandId(),
                        pendingDispatch.action()))
                .toList();

        DeviceCommandResp commandResp = callDeviceService(commands);

        Map<String, DeviceCommandResp.CommandResult> resultByCommandId = new HashMap<>();
        if (commandResp.getResults() != null) {
            for (DeviceCommandResp.CommandResult result : commandResp.getResults()) {
                if (result == null || result.getCommandId() == null) {
                    continue;
                }
                resultByCommandId.put(result.getCommandId(), result);
            }
        }

        // 保存批量日志记录（每个设备一条）
        List<DeviceCommandLog> logs = pending.stream()
                .map(pendingDispatch -> buildDeviceCommandLog(
                        userId,
                        pendingDispatch.deviceId(),
                        pendingDispatch.commandId(),
                        pendingDispatch.action(),
                        resultByCommandId.get(pendingDispatch.commandId()),
                        pendingDispatch.action() != null ? pendingDispatch.action().getTtlMinutes() : null))
                .toList();
        deviceCommandLogRepository.saveAll(logs);

        publishBatchOperationUpdatedBestEffort(logs);

        List<DeviceActionDispatchResp> results = pending.stream()
                .map(pendingDispatch -> deviceActionDispatchConverter.toDispatchResp(
                        pendingDispatch.deviceId(),
                        pendingDispatch.commandId(),
                        pendingDispatch.action(),
                        resultByCommandId.get(pendingDispatch.commandId())))
                .toList();

        // accepted=false 的指令不会产生 confirm/expired 等后续事件，需要在此处直接计入失败并移出 pending。
        for (DeviceActionDispatchResp result : results) {
            if (result == null || result.getOperationId() == null) {
                continue;
            }
            if (!result.isAccepted()) {
                messageCenterFacade.onBatchCommandFinalState(result.getOperationId(), userId, false, false);
            }
        }

        int accepted = (int) results.stream().filter(DeviceActionDispatchResp::isAccepted).count();
        return BatchDeviceActionDispatchResp.builder()
                .batchOperationId(batchOperationId)
                .total(results.size())
                .accepted(accepted)
                .results(results)
                .build();
    }

    private void publishBatchOperationUpdatedBestEffort(List<DeviceCommandLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        if (logs.size() > MAX_BATCH_OPERATION_SSE) {
            return;
        }
        for (DeviceCommandLog log : logs) {
            publishOperationUpdatedBestEffort(log);
        }
    }

    private void publishOperationUpdatedBestEffort(DeviceCommandLog commandLog) {
        if (commandLog == null || commandLog.getUserId() == null) {
            return;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("operationType", "DEVICE_COMMAND");
            if (commandLog.getStatus() != null) {
                data.put("status", commandLog.getStatus().name());
            }
            if (commandLog.getActionType() != null) {
                data.put("actionType", commandLog.getActionType().name());
            }
            if (commandLog.getTrackingLevel() != null) {
                data.put("trackingLevel", commandLog.getTrackingLevel().name());
            }
            data.put("accepted", commandLog.isAccepted());
            data.put("covered", commandLog.isCovered());
            if (commandLog.getSendMethod() != null) {
                data.put("sendMethod", commandLog.getSendMethod());
            }
            if (commandLog.getQueuedId() != null) {
                data.put("queuedId", commandLog.getQueuedId());
            }
            if (commandLog.getErrorMessage() != null) {
                data.put("errorMessage", commandLog.getErrorMessage());
            }

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(SSE_TYPE_OPERATION_UPDATED)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(commandLog.getUserId())
                            .deviceId(commandLog.getDeviceId())
                            .operationId(commandLog.getOperationId().toString())
                            .build())
                    .data(data)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_OPERATION_UPDATED, message);
        } catch (Exception ex) {
            // best-effort：不影响主业务流程（指令已落库并返回给前端）
            log.debug("operation.updated publish failed (ignored): userId={}, deviceId={}, operationId={}",
                    commandLog.getUserId(), commandLog.getDeviceId(), commandLog.getOperationId().toString(), ex);
        }
    }

    private record PendingDispatch(Long deviceId, String commandId, DeviceActionBase action) {
    }

    /**
     * 确保用户拥有设备
     * @param userId 用户Id
     * @param deviceId 设备Id
     */
    private void ensureUserOwnsDevice(UUID userId, Long deviceId) {
        DeviceEntity device = deviceRepository.findByDeviceIdAndUserId(deviceId, userId);
        if (device == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
    }

    private DeviceCommandResp callDeviceService(List<DeviceCommandReq> commands) {
        ResponseEntity<ApiResponse<DeviceCommandResp>> response;
        try {
            response = deviceInternalClient.sendCommand(commands);
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用 device-service 下发指令失败", ex);
        }

        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回 null ResponseEntity");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service HTTP 状态异常: " + response.getStatusCode());
        }

        ApiResponse<DeviceCommandResp> body = response.getBody();
        if (body == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空响应体");
        }
        if (!isDeviceServiceSuccessCode(body.getCode())) {
            throw new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "device-service 返回失败: code=" + body.getCode() + ", message=" + body.getMessage());
        }

        DeviceCommandResp resp = body.getData();
        if (resp == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空 data");
        }
        return resp;
    }

    private DeviceCommandResp.CommandResult findResult(DeviceCommandResp resp, String commandId) {
        if (resp == null || resp.getResults() == null || resp.getResults().isEmpty()) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空 results: commandId=" + commandId);
        }
        for (DeviceCommandResp.CommandResult commandResult : resp.getResults()) {
            if (commandResult == null) {
                continue;
            }
            if (commandId.equals(commandResult.getCommandId())) {
                return commandResult;
            }
        }
        // 兜底：若下游未回填 commandId，则返回第一条结果
        return resp.getResults().getFirst();
    }

    /**
     * 构建指令日志
     * @param userId 用户Id
     * @param deviceId 设备Id
     * @param commandId 指令Id
     * @param action 指令操作封装
     * @param result 结果
     * @param commandTtl 指令 TTL
     * @return 指令日志
     */
    private DeviceCommandLog buildDeviceCommandLog(
            UUID userId,
            Long deviceId,
            String commandId,
            DeviceActionBase action,
            DeviceCommandResp.CommandResult result,
            Long commandTtl) {

        boolean accepted = result != null && result.isAccepted();
        Long effectiveTtlMinutes = commandTtl != null && commandTtl > 0 ? commandTtl : 60L;
        return DeviceCommandLog.builder()
                .id(IdGenerator.nextId())
                .userId(userId)
                .deviceId(deviceId)
                .operationId(UUID.fromString(commandId))
                .actionType(action != null ? action.getType() : null)
                .trackingLevel(action != null && action.getType() != null ? action.getType().getTrackingLevel() : null)
                .status(accepted ? DeviceCommandStatus.PUBLISHED : DeviceCommandStatus.FAILED)
                .payload(JsonUtils.toJson(action != null ? action.getBody() : null))
                .ttlMinutes(effectiveTtlMinutes)
                .sendMethod(result != null ? result.getSendMethod() : null)
                .queuedId(result != null ? result.getQueuedId() : null)
                .accepted(accepted)
                .covered(result != null && result.isCovered())
                .errorMessage(result != null ? result.getErrorMessage() : "device-service 未返回结果")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private boolean isDeviceServiceSuccessCode(String code) {
        return "200".equals(code) || ErrorCode.SUCCESS.getCode().equals(code);
    }
}
