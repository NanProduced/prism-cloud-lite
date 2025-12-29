package nan.produced.prism.device.infrastructure.cache.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.port.outbound.command.DeviceCommandQueuePort;
import nan.produced.prism.device.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static nan.produced.prism.device.infrastructure.cache.redis.config.RedisKeyConstant.*;

/**
 * 指令队列 Redis 适配器
 *
 * <p>说明：</p>
 * <ul>
 *   <li>队列：List（terminal:commands:{deviceId}）存放 queuedId</li>
 *   <li>详情：String（terminal:command:detail:{deviceId}:{queuedId}）存放指令详情</li>
 *   <li>去重：Hash（terminal:command:index:{deviceId}）dedupeKey -> queuedId</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandQueueRedisAdapter implements DeviceCommandQueuePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final long DEFAULT_TTL_MINUTES = 60L;

    /**
     * 缓存指令
     *
     * @param command 指令
     * @return 缓存结果
     */
    @Override
    public Pair<Boolean, Boolean> cacheCommand(DeviceCommand command) {
        if (command == null || command.getDeviceId() == null || command.getQueueId() == null) {
            return Pair.of(false, false);
        }

        String queueKey = String.format(COMMAND_QUEUE_KEY, command.getDeviceId());
        String indexKey = String.format(COMMAND_INDEX_KEY, command.getDeviceId());
        String detailKey = String.format(COMMAND_DETAIL_KEY, command.getDeviceId(), command.getQueueId());

        boolean covered = false;

        // 特殊处理，如果authorUrl为null，使用空字符串代替，避免后续hash操作失败
        String indexAuthorUrl = StringUtils.defaultIfBlank(command.getAuthorUrl(), "");

        try {
            // 1. 检查是否存在相同类型的指令（去重）
            Object existingQueueIdObj = redisTemplate.opsForHash().get(indexKey, indexAuthorUrl);
            if (existingQueueIdObj != null) {
                Integer existingQueueId = Integer.valueOf(existingQueueIdObj.toString());
                String existingDetailKey = String.format(COMMAND_DETAIL_KEY, command.getDeviceId(), existingQueueId);

                // 懒清理：当索引指向的详情已过期（不存在）时，仅做自愈清理，不视为覆盖
                boolean exists = redisTemplate.hasKey(existingDetailKey);
                if (!exists) {
                    log.debug("DeviceCommandQueue - 发现过期索引，执行懒清理, authorUrl: {}, oldQueueId: {}",
                            command.getAuthorUrl(), existingQueueId);
                    redisTemplate.opsForHash().delete(indexKey, indexAuthorUrl);
                } else {
                    log.debug("DeviceCommandQueue - 发现重复指令类型，执行覆盖操作, authorUrl: {}, oldQueueId: {}",
                            command.getAuthorUrl(), existingQueueId);
                    // 移除旧指令
                    removeOldCommand(command.getDeviceId(), existingQueueId);
                    covered = true;
                }
            }

            // 2. 缓存新指令
            command.setCacheTime(LocalDateTime.now());
            command.setTtlMinutes(resolveTtlMinutes(command));
            String commandJson = JsonUtils.toJson(command);
            redisTemplate.opsForValue().set(detailKey, commandJson, command.getTtlMinutes(), TimeUnit.MINUTES);

            // 队列无法给单个元素加TTL，缓存指令详情时已经加了TTL，缓存过期后设备通过队列拉取不到过期指令，效果一致
            // 去重索引采用“懒清理”机制：在取指令/去重覆盖时发现详情不存在，则自愈清理
            // 队列使用Redis TTL监听指令详情的TTL，当详情过期时，从队列中移除该指令

            // 3. 添加到指令队列（右推入，保持时间顺序）
            redisTemplate.opsForList().rightPush(queueKey, command.getQueueId());

            // 4. 更新去重索引
            redisTemplate.opsForHash().put(indexKey, indexAuthorUrl, command.getQueueId());

        } catch (Exception e) {
            log.error("DeviceCommandQueue - 缓存指令异常, command: {}", command, e);
            return Pair.of(false, false);
        }

        return Pair.of(true, covered);
    }

    private long resolveTtlMinutes(DeviceCommand command) {
        if (command == null) {
            return DEFAULT_TTL_MINUTES;
        }
        Long ttlMinutes = command.getTtlMinutes();
        if (ttlMinutes != null && ttlMinutes > 0) {
            return ttlMinutes;
        }
        if (command.getExpireTime() != null) {
            long minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), command.getExpireTime());
            return Math.max(1L, minutes);
        }
        return DEFAULT_TTL_MINUTES;
    }

    /**
     * 获取待处理的指令
     *
     * @param deviceId 设备ID
     * @return 待处理的指令列表
     */
    @Override
    public List<DeviceCommand> getPendingCommands(Long deviceId) {

        String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);

        List<Object> pipelineResult = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    // 获取指令ID队列
                    connection.listCommands().lRange(queueKey.getBytes(StandardCharsets.UTF_8), 0, -1);
                    return null;
                }
        );

        if (pipelineResult.isEmpty()) return List.of();

        @SuppressWarnings("unchecked")
        List<Object> queueIds = (List<Object>) pipelineResult.getFirst();
        if (queueIds.isEmpty()) {
            return List.of();
        }

        // 批量获取指令详情
        List<String> detailKeys = queueIds.stream()
                .map(id -> String.format(COMMAND_DETAIL_KEY, deviceId, Integer.valueOf(id.toString())))
                .toList();

        List<Object> detailResults = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    // 批量获取所有指令详情
                    for (String detailKey : detailKeys) {
                        connection.stringCommands().get(detailKey.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                }
        );

        return parseCommandResult(detailResults, deviceId);
    }

    /**
     * 获取指令详情
     *
     * @param deviceId 设备ID
     * @param queueId  指令ID
     * @return 指令详情
     */
    @Override
    public Optional<DeviceCommand> getCommand(Long deviceId, Integer queueId) {
        try {
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, queueId);
            Object commandJson = redisTemplate.opsForValue().get(detailKey);

            if (commandJson != null) {
                DeviceCommand command = JsonUtils.fromJson(commandJson.toString(), DeviceCommand.class);
                return Optional.of(command);
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("DeviceCommandQueue - 获取指令详情异常, deviceId: {}, queueId: {}", deviceId, queueId, e);
            return Optional.empty();
        }
    }

    /**
     * 移除指令
     * @param deviceId 设备ID
     * @param queueId 指令ID
     * @return 移除的指令
     */
    @Override
    public DeviceCommand removeCommand(Long deviceId, Integer queueId) {
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            String indexKey = String.format(COMMAND_INDEX_KEY, deviceId);
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, queueId);

            // 获取指令详情以确定authorUrl
            Optional<DeviceCommand> commandOpt = getCommand(deviceId, queueId);

            // 从队列中移除
            redisTemplate.opsForList().remove(queueKey, 1, queueId);

            // 从索引中移除(特殊处理，如果为null则转为空字符串)
            commandOpt.ifPresent(command -> redisTemplate.opsForHash().delete(indexKey, command.getAuthorUrl() == null ? "" : command.getAuthorUrl()));

            // 删除详情
            redisTemplate.delete(detailKey);

            return commandOpt.orElse(null);

        } catch (Exception e) {
            log.error("DeviceCommandQueue - 移除指令异常, deviceId: {}, queueId: {}", deviceId, queueId, e);
            return null;
        }
    }

    @Override
    public void removeFromQueue(Long deviceId, Integer queueId) {
        String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);

        redisTemplate.opsForList().remove(queueKey, 1, queueId);
    }

    /**
     * 移除旧指令 (去重时使用)
     *
     * @param deviceId 设备ID
     * @param oldQueueId 旧指令ID
     */
    private void removeOldCommand(Long deviceId, Integer oldQueueId) {
        try {
            String queueKey = String.format(COMMAND_QUEUE_KEY, deviceId);
            String detailKey = String.format(COMMAND_DETAIL_KEY, deviceId, oldQueueId);

            // 从队列移除
            redisTemplate.opsForList().remove(queueKey, 0, oldQueueId);

            // 删除详情
            redisTemplate.delete(detailKey);

            log.debug("DeviceCommandQueue - 旧指令移除成功, deviceId: {}, oldCommandId: {}", deviceId, oldQueueId);

        } catch (Exception e) {
            log.warn("DeviceCommandQueue - 移除旧指令异常, deviceId: {}, oldCommandId: {}", deviceId, oldQueueId, e);
        }
    }

    /**
     * 解析指令结果
     * @param commandResult 指令结果
     * @param deviceId 设备ID
     * @return 指令列表
     */
    private List<DeviceCommand> parseCommandResult(List<Object> commandResult, Long deviceId) {
        List<DeviceCommand> commands = new ArrayList<>();

        for (Object commandData : commandResult) {
            if (commandData != null) {
                try {
                    String commandJson = "";
                    if (commandData instanceof  byte[] commandBytes) {
                        commandJson = new String(commandBytes, StandardCharsets.UTF_8);
                    }

                    DeviceCommand command = JsonUtils.fromJson(commandJson, DeviceCommand.class);

                    if (command != null && !command.isExpired()) {
                        commands.add(command);
                    }
                } catch (Exception e) {
                    log.warn("DeviceCommandQueue - 解析指令结果异常, deviceId: {}, commandData: {}", deviceId, commandData, e);
                }
            }
        }

        log.debug("DeviceCommandQueue - 获取指令成功, deviceId: {}, commandCount: {}", deviceId, commands.size());
        return commands;
    }
}
