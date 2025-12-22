package nan.produced.prism.core.device.infrastructure.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandFeedBackPort;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandLogRepository;
import nan.produced.prism.core.device.domain.DeviceProperties;
import nan.produced.prism.core.device.domain.command.DeviceActionTrackingLevel;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceCommandFeedBackHandler implements DeviceCommandFeedBackPort {

    private final DeviceCommandLogRepository deviceCommandLogRepository;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String COMMAND_LISTENER_KEY = "command:listener:%d:%s";

    @Override
    public void handleCommandConfirm(String commandId, Long deviceId, Integer queueId) {
        DeviceCommandLog commandLog = deviceCommandLogRepository.findByOperationId(commandId);
        if (commandLog ==  null) {
            log.warn("DeviceCommandFeedBackHandler - 找不到指令日志(异常指令), commandId={}, queueId={}", commandId, queueId);
            return;
        }
        // 确认指令
        deviceCommandLogRepository.updateStatus(commandId, DeviceCommandStatus.CONFIRMED);
        // 如果需要根据属性上报监听上报状态
        if (commandLog.getTrackingLevel().equals(DeviceActionTrackingLevel.PROPERTY_MATCH)) {
            // 根据设备Id和指令类型监听指令结果
            String listenerKey = String.format(COMMAND_LISTENER_KEY, deviceId, commandLog.getActionType().name());
            long ttl = commandLog.getTtlMinutes() != null ? commandLog.getTtlMinutes() : 60;
            // 缓存指令的Id以便后续更新状态
            redisTemplate.opsForValue().set(listenerKey, commandId, ttl, TimeUnit.MINUTES);
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
        if (keys.isEmpty()) return;
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
            log.warn("DeviceCommandFeedBackHandler - 找不到指令日志(异常指令), deviceId={}, queueId={}", deviceId, queueId);
            return;
        }

        // 标记指令已过期
        deviceCommandLogRepository.updateStatus(commandLog.getOperationId(), DeviceCommandStatus.EXPIRED);
    }

    /**
     * 标记指令已完成
     * @param key 缓存的key
     */
    private void markCommandCompleted(String key) {
        String commandId = (String) redisTemplate.opsForValue().getAndDelete(key);
        deviceCommandLogRepository.updateStatus(commandId, DeviceCommandStatus.COMPLETED);
    }

}
