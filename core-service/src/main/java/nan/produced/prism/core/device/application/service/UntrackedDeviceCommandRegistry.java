package nan.produced.prism.core.device.application.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 方案B：未落库的 device command 标记注册表（用于回执侧识别并静默忽略）。
 *
 * <p>适用场景：program/schedule 等模块直接下发 raw 指令，不走 DeviceCommandLog 落库，但 device-service 仍会回执 confirm/expired。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UntrackedDeviceCommandRegistry {

    private static final long TTL_HOURS = 48;

    private static final String KEY_BY_COMMAND_ID = "cmd:untracked:id:%s";
    private static final String KEY_BY_QUEUE = "cmd:untracked:q:%d:%d";

    private final StringRedisTemplate redisTemplate;

    public void markByCommandId(String commandId, String type) {
        if (!StringUtils.hasText(commandId) || !StringUtils.hasText(type)) {
            return;
        }
        redisTemplate.opsForValue().set(
                String.format(KEY_BY_COMMAND_ID, commandId.trim()),
                type.trim(),
                TTL_HOURS,
                TimeUnit.HOURS);
    }

    public void markByQueue(Long deviceId, Integer queueId, String commandId, String type) {
        if (deviceId == null || deviceId <= 0 || queueId == null || queueId <= 0) {
            return;
        }
        if (!StringUtils.hasText(type)) {
            return;
        }
        String value = type.trim();
        if (StringUtils.hasText(commandId)) {
            value = value + "|" + commandId.trim();
        }
        redisTemplate.opsForValue().set(
                String.format(KEY_BY_QUEUE, deviceId, queueId),
                value,
                TTL_HOURS,
                TimeUnit.HOURS);
    }

    public String findTypeByCommandId(String commandId) {
        if (!StringUtils.hasText(commandId)) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(String.format(KEY_BY_COMMAND_ID, commandId.trim()));
        } catch (Exception e) {
            log.debug("UntrackedDeviceCommandRegistry - findTypeByCommandId failed: commandId={}", commandId, e);
            return null;
        }
    }

    public String findValueByQueue(Long deviceId, Integer queueId) {
        if (deviceId == null || deviceId <= 0 || queueId == null || queueId <= 0) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(String.format(KEY_BY_QUEUE, deviceId, queueId));
        } catch (Exception e) {
            log.debug("UntrackedDeviceCommandRegistry - findValueByQueue failed: deviceId={}, queueId={}", deviceId, queueId, e);
            return null;
        }
    }
}

