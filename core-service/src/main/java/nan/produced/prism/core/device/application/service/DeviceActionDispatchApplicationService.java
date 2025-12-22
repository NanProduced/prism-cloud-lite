package nan.produced.prism.core.device.application.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
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
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceActionDispatchApplicationService implements DeviceActionDispatchUseCase {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandLogRepository deviceCommandLogRepository;
    private final DeviceInternalClient deviceInternalClient;
    private final DeviceActionDispatchConverter deviceActionDispatchConverter;

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

        DeviceCommandLog log = buildDeviceCommandLog(userId, action, result, commandReq.getTtlMinutes());
        // 保存日志记录
        deviceCommandLogRepository.save(log);

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

        List<DeviceActionDispatchResp> results = pending.stream()
                .map(pendingDispatch -> deviceActionDispatchConverter.toDispatchResp(
                        pendingDispatch.deviceId(),
                        pendingDispatch.commandId(),
                        pendingDispatch.action(),
                        resultByCommandId.get(pendingDispatch.commandId())))
                .toList();

        int accepted = (int) results.stream().filter(DeviceActionDispatchResp::isAccepted).count();
        return BatchDeviceActionDispatchResp.builder()
                .total(results.size())
                .accepted(accepted)
                .results(results)
                .build();
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
     * @param action 指令操作封装
     * @param result 结果
     * @param commandTtl 指令 TTL
     * @return 指令日志
     */
    private DeviceCommandLog buildDeviceCommandLog(UUID userId, DeviceActionBase action, DeviceCommandResp.CommandResult result, Long commandTtl) {

        return DeviceCommandLog.builder()
                .id(IdGenerator.nextId())
                .userId(userId)
                .deviceId(result.getDeviceId())
                .operationId(result.getCommandId())
                .actionType(action.getType())
                .trackingLevel(action.getType().getTrackingLevel())
                .status(result.isAccepted() ? DeviceCommandStatus.PUBLISHED : DeviceCommandStatus.FAILED)
                .payload(JsonUtils.toJson(action.getBody()))
                .ttlMinutes(commandTtl)
                .sendMethod(result.getSendMethod())
                .queuedId(result.getQueuedId())
                .accepted(result.isAccepted())
                .covered(result.isCovered())
                .errorMessage(result.getErrorMessage())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private boolean isDeviceServiceSuccessCode(String code) {
        return "200".equals(code) || ErrorCode.SUCCESS.getCode().equals(code);
    }
}
