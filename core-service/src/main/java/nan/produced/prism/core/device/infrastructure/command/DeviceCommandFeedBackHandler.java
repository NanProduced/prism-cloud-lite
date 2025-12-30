package nan.produced.prism.core.device.infrastructure.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandFeedBackPort;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandLogRepository;
import nan.produced.prism.core.device.application.service.UntrackedDeviceCommandRegistry;
import nan.produced.prism.core.device.domain.DeviceProperties;
import nan.produced.prism.core.device.domain.command.DeviceActionTrackingLevel;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceCommandFeedBackHandler implements DeviceCommandFeedBackPort {

    private final DeviceCommandLogRepository deviceCommandLogRepository;

    private final StringRedisTemplate redisTemplate;

    private final RabbitMessagePublisher rabbitMessagePublisher;

    private final MessageCenterFacade messageCenterFacade;

    private final DeviceRepositoryJpa deviceRepositoryJpa;

    private final UntrackedDeviceCommandRegistry untrackedDeviceCommandRegistry;

    private static final String COMMAND_LISTENER_KEY = "command:listener:%d:%s";

    @Override
    public void handleCommandConfirm(String commandId, Long deviceId, Integer queueId) {
        DeviceCommandLog commandLog = deviceCommandLogRepository.findByOperationId(commandId);
        if (commandLog ==  null) {
            String untrackedMark = untrackedDeviceCommandRegistry.findTypeByCommandId(commandId);
            if (!StringUtils.hasText(untrackedMark) && deviceId != null && queueId != null) {
                untrackedMark = untrackedDeviceCommandRegistry.findValueByQueue(deviceId, queueId);
            }
            if (StringUtils.hasText(untrackedMark)) {
                log.debug("DeviceCommandFeedBackHandler - 忽略未追踪指令回执, mark={}, commandId={}, deviceId={}, queueId={}",
                        untrackedMark, commandId, deviceId, queueId);
                return;
            }

            log.warn("DeviceCommandFeedBackHandler - 找不到指令日志(异常指令), commandId={}, queueId={}", commandId, queueId);
            return;
        }
        // 确认指令
        deviceCommandLogRepository.updateStatus(commandId, DeviceCommandStatus.CONFIRMED);
        // 推送 SSE：operation.updated
        publishOperationUpdated(commandLog, DeviceCommandStatus.CONFIRMED);
        // 如果需要根据属性上报监听上报状态
        if (commandLog.getTrackingLevel().equals(DeviceActionTrackingLevel.PROPERTY_MATCH)) {
            // 根据设备Id和指令类型监听指令结果
            String listenerKey = String.format(COMMAND_LISTENER_KEY, deviceId, commandLog.getActionType().name());
            long ttl = commandLog.getTtlMinutes() != null ? commandLog.getTtlMinutes() : 60;
            // 缓存指令的Id以便后续更新状态
            redisTemplate.opsForValue().set(listenerKey, commandId, ttl, TimeUnit.MINUTES);
            return;
        }

        boolean inBatch = messageCenterFacade.onBatchCommandFinalState(commandId, commandLog.getUserId(), true, false);
        if (!inBatch) {
            publishDeviceCommandMessage(commandLog, DeviceCommandStatus.CONFIRMED);
        }
    }

    /**
     * 处理指令结果
     * <p>根据指令上报类型找到对应的指令，然后更新指令状态，不需要验证结果(因为设备端只有成功执行了指令才会上报)</p>
     * @param deviceId 设备ID
     * @param properties 设备属性
     */
    @Override
    public void chackCommandResult(Long deviceId, DeviceProperties properties) {
        // 查询出这个设备正在等待指令执行结果的指令
        Set<String> keys = redisTemplate.keys("command:listener:" + deviceId + ":*");
        if (keys == null || keys.isEmpty()) return;
        //
        for (String key : keys) {
            int lastIndex = key.lastIndexOf(':');
            DeviceActionType actionType;
            try {
                actionType = DeviceActionType.valueOf(key.substring(lastIndex + 1));
            } catch (IllegalArgumentException e) {
                log.warn("DeviceCommandFeedBackHandler - 监听的指令类型不存在, key={}", key);
                return;
            }
            switch (actionType) {
                // 这里如果同时发了亮度和色温指令，那么也会上报两条数据，所以是幂等的
                case BRIGHTNESS, COLOR_TEMP:
                    if (properties.getBrightnessandcolortemp() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case VOLUME:
                    if (properties.getVolume() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case CLEAR_CACHE:
                    DeviceProperties.InfoWrapper infoWrapper = properties.getInfo();
                    DeviceProperties.Info info = infoWrapper != null ? infoWrapper.getInfo() : null;
                    if (info != null && info.getStorage() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case INPUT_MODE:
                    if (properties.getInputmode() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case TIMEZONE:
                    if (properties.getNewrtc() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case LOCALE:
                    if (properties.getLocale() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                case CONTENT_REPORT_SWITCH:
                    if (properties.getContentreport() != null) {
                        markCommandCompleted(key);
                    }
                    break;
                default:
            }

        }

    }

    @Override
    public void handleCommandExpired(Long deviceId, Integer queueId) {
        DeviceCommandLog commandLog = deviceCommandLogRepository.findByDeviceIdAndQueueId(deviceId, queueId);
        if (commandLog == null) {
            String untrackedMark = untrackedDeviceCommandRegistry.findValueByQueue(deviceId, queueId);
            if (StringUtils.hasText(untrackedMark)) {
                log.debug("DeviceCommandFeedBackHandler - 忽略未追踪指令过期回执, mark={}, deviceId={}, queueId={}",
                        untrackedMark, deviceId, queueId);
                return;
            }
            log.warn("DeviceCommandFeedBackHandler - 找不到指令日志(异常指令), deviceId={}, queueId={}", deviceId, queueId);
            return;
        }

        // 标记指令已过期
        deviceCommandLogRepository.updateStatus(commandLog.getOperationId().toString(), DeviceCommandStatus.EXPIRED);
        clearCommandListener(commandLog);
        publishOperationUpdated(commandLog, DeviceCommandStatus.EXPIRED);

        boolean inBatch = messageCenterFacade.onBatchCommandFinalState(commandLog.getOperationId().toString(), commandLog.getUserId(), false, true);
        if (!inBatch) {
            publishDeviceCommandMessage(commandLog, DeviceCommandStatus.EXPIRED);
        }
    }

    /**
     * 标记指令已完成
     * @param key 缓存的key
     */
    private void markCommandCompleted(String key) {
        String commandId = (String) redisTemplate.opsForValue().getAndDelete(key);
        if (commandId == null || commandId.isBlank()) {
            return;
        }

        DeviceCommandLog commandLog = deviceCommandLogRepository.findByOperationId(commandId);
        if (commandLog == null) {
            log.warn("DeviceCommandFeedBackHandler - 指令完成但找不到日志, commandId={}", commandId);
            deviceCommandLogRepository.updateStatus(commandId, DeviceCommandStatus.COMPLETED);
            return;
        }

        deviceCommandLogRepository.updateStatus(commandId, DeviceCommandStatus.COMPLETED);
        // 推送 SSE：operation.updated
        publishOperationUpdated(commandLog, DeviceCommandStatus.COMPLETED);

        boolean inBatch = messageCenterFacade.onBatchCommandFinalState(commandId, commandLog.getUserId(), true, false);
        if (!inBatch) {
            publishDeviceCommandMessage(commandLog, DeviceCommandStatus.COMPLETED);
        }
    }

    private void publishOperationUpdated(DeviceCommandLog commandLog, DeviceCommandStatus status) {
        if (commandLog == null || commandLog.getUserId() == null) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("operationType", "DEVICE_COMMAND");
        if (status != null) {
            data.put("status", status.name());
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
                .type("operation.updated")
                .scope(FrontendEventMessage.Scope.builder()
                        .userId(commandLog.getUserId())
                        .deviceId(commandLog.getDeviceId())
                        .operationId(commandLog.getOperationId().toString())
                        .build())
                .data(data)
                .build();

        rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_OPERATION_UPDATED, message);
    }

    private void publishDeviceCommandMessage(DeviceCommandLog commandLog, DeviceCommandStatus finalStatus) {
        if (commandLog == null || commandLog.getUserId() == null || commandLog.getDeviceId() == null) {
            return;
        }

        String actionType = commandLog.getActionType() != null ? commandLog.getActionType().name() : "UNKNOWN";
        String deviceNameSnapshot = null;
        try {
            var device = deviceRepositoryJpa.findByDeviceIdAndUserId(commandLog.getDeviceId(), commandLog.getUserId());
            if (device != null) {
                deviceNameSnapshot = device.getDeviceName();
            }
        } catch (Exception ignore) {
            deviceNameSnapshot = null;
        }

        messageCenterFacade.publishDeviceCommandFinished(new MessageCenterFacade.DeviceCommandFinishedMessage(
            commandLog.getUserId(),
            commandLog.getDeviceId(),
            deviceNameSnapshot,
            commandLog.getOperationId().toString(),
            actionType,
            commandLog.getTrackingLevel() != null ? commandLog.getTrackingLevel().name() : null,
            finalStatus != null ? finalStatus.name() : null,
            commandLog.isAccepted(),
            commandLog.isCovered(),
            commandLog.getSendMethod(),
            commandLog.getQueuedId(),
            commandLog.getErrorMessage()
        ));
    }

    private void clearCommandListener(DeviceCommandLog commandLog) {
        if (commandLog == null || commandLog.getDeviceId() == null || commandLog.getActionType() == null) {
            return;
        }
        String listenerKey = String.format(COMMAND_LISTENER_KEY, commandLog.getDeviceId(), commandLog.getActionType().name());
        redisTemplate.delete(listenerKey);
    }

}
