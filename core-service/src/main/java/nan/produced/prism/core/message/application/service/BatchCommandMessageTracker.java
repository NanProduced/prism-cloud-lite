package nan.produced.prism.core.message.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 批量指令消息聚合器：将多条指令的最终状态聚合为一条消息中心通知。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>accepted=false 的指令在创建批量时即视为失败（不再等待设备回执）</li>
 *   <li>accepted=true 的指令等待最终态：ACK_ONLY/UPDATE_ONLY 以 CONFIRMED 视为完成；PROPERTY_MATCH 以 COMPLETED 视为完成</li>
 *   <li>EXPIRED 视为失败</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchCommandMessageTracker {

    private static final long TTL_HOURS = 48;

    private static final String KEY_META = "msg:batchcmd:meta:%s";
    private static final String KEY_PENDING = "msg:batchcmd:pending:%s";
    private static final String KEY_INDEX = "msg:batchcmd:idx:%s";
    private static final String KEY_NOTIFIED = "msg:batchcmd:notified:%s";

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_ACCEPTED = "accepted";
    private static final String FIELD_SUCCESS = "success";
    private static final String FIELD_FAILED = "failed";
    private static final String FIELD_EXPIRED = "expired";
    private static final String FIELD_ACTION_TYPES = "actionTypes";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final String MESSAGE_TYPE_BATCH_COMMAND_FINISHED = "device.command.batch.finished";

    private final StringRedisTemplate redisTemplate;
    private final MessageWriteApplicationService messageWriteApplicationService;

    public void start(UUID userId, String batchOperationId, Collection<MessageCenterFacade.BatchCommandItem> items) {
        if (userId == null || !StringUtils.hasText(batchOperationId) || items == null || items.isEmpty()) {
            return;
        }

        int total = items.size();
        int accepted = 0;
        Set<String> actionTypes = new LinkedHashSet<>();
        for (MessageCenterFacade.BatchCommandItem item : items) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(item.actionType())) {
                actionTypes.add(item.actionType());
            }
            if (item.accepted()) {
                accepted++;
            }
        }
        int immediateFailed = total - accepted;

        String metaKey = metaKey(batchOperationId);
        Map<String, String> meta = new HashMap<>();
        meta.put(FIELD_USER_ID, userId.toString());
        meta.put(FIELD_TOTAL, String.valueOf(total));
        meta.put(FIELD_ACCEPTED, String.valueOf(accepted));
        meta.put(FIELD_SUCCESS, "0");
        meta.put(FIELD_FAILED, String.valueOf(immediateFailed));
        meta.put(FIELD_EXPIRED, "0");
        meta.put(FIELD_ACTION_TYPES, String.join(",", actionTypes));
        meta.put(FIELD_CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC).toString());

        redisTemplate.opsForHash().putAll(metaKey, meta);
        redisTemplate.expire(metaKey, TTL_HOURS, TimeUnit.HOURS);

        String pendingKey = pendingKey(batchOperationId);
        for (MessageCenterFacade.BatchCommandItem item : items) {
            if (item == null || !item.accepted() || !StringUtils.hasText(item.commandId())) {
                continue;
            }
            String commandId = item.commandId().trim();
            redisTemplate.opsForSet().add(pendingKey, commandId);
            redisTemplate.opsForValue().set(indexKey(commandId), batchOperationId, TTL_HOURS, TimeUnit.HOURS);
        }
        redisTemplate.expire(pendingKey, TTL_HOURS, TimeUnit.HOURS);

        if (accepted == 0) {
            tryFinalize(batchOperationId, userId);
        }
    }

    /**
     * 指令进入最终态时调用；若该指令属于某个批量操作，则更新批量进度并在完成时生成消息。
     *
     * @return true 表示该指令属于批量操作（无论是否重复回调）；false 表示不属于批量
     */
    public boolean onCommandFinalState(String commandId, UUID userId, boolean success, boolean expired) {
        if (!StringUtils.hasText(commandId) || userId == null) {
            return false;
        }

        String batchId = redisTemplate.opsForValue().get(indexKey(commandId.trim()));
        if (!StringUtils.hasText(batchId)) {
            return false;
        }

        String pendingKey = pendingKey(batchId);
        Long removed = redisTemplate.opsForSet().remove(pendingKey, commandId.trim());
        if (removed == null || removed <= 0) {
            return true;
        }

        String metaKey = metaKey(batchId);
        if (success) {
            redisTemplate.opsForHash().increment(metaKey, FIELD_SUCCESS, 1);
        } else {
            redisTemplate.opsForHash().increment(metaKey, FIELD_FAILED, 1);
            if (expired) {
                redisTemplate.opsForHash().increment(metaKey, FIELD_EXPIRED, 1);
            }
        }

        Long remaining = redisTemplate.opsForSet().size(pendingKey);
        if (remaining != null && remaining == 0) {
            tryFinalize(batchId, userId);
        }

        return true;
    }

    private void tryFinalize(String batchOperationId, UUID userId) {
        if (userId == null || !StringUtils.hasText(batchOperationId)) {
            return;
        }

        String notifiedKey = notifiedKey(batchOperationId);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(notifiedKey, "1", TTL_HOURS, TimeUnit.HOURS);
        if (first == null || !first) {
            return;
        }

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey(batchOperationId));
        long total = asLong(meta.get(FIELD_TOTAL));
        long success = asLong(meta.get(FIELD_SUCCESS));
        long failed = asLong(meta.get(FIELD_FAILED));
        String actionTypes = asString(meta.get(FIELD_ACTION_TYPES));

        MessageStatus status = failed == 0 ? MessageStatus.SUCCESS : MessageStatus.FAILED;
        String title = failed == 0 ? "批量指令已完成" : "批量指令已完成（部分失败）";
        String summary = String.format("共 %d 条，成功 %d 条，失败 %d 条", total, success, failed);

        Map<String, Object> payload = new HashMap<>();
        payload.put("operationType", "DEVICE_COMMAND_BATCH");
        payload.put("batchOperationId", batchOperationId);
        payload.put("total", total);
        payload.put("success", success);
        payload.put("failed", failed);
        if (StringUtils.hasText(actionTypes)) {
            payload.put("actionTypes", List.of(actionTypes.split(",")));
        }

        messageWriteApplicationService.createAndPublish(
            MessageKind.NOTIFICATION,
            MESSAGE_TYPE_BATCH_COMMAND_FINISHED,
            status,
            userId,
            title,
            summary,
            payload,
            null,
            null,
            null,
            null,
            batchOperationId,
            null
        );
    }

    private String metaKey(String batchOperationId) {
        return String.format(KEY_META, batchOperationId);
    }

    private String pendingKey(String batchOperationId) {
        return String.format(KEY_PENDING, batchOperationId);
    }

    private String indexKey(String commandId) {
        return String.format(KEY_INDEX, commandId);
    }

    private String notifiedKey(String batchOperationId) {
        return String.format(KEY_NOTIFIED, batchOperationId);
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
