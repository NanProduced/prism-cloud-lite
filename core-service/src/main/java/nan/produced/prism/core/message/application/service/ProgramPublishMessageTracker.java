package nan.produced.prism.core.message.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 节目发布消息聚合器：基于设备下载完成（DOWNLOADED）上报，聚合生成“节目发布成功”消息。
 *
 * <p>额外规则（根据产品确认）：若目标设备列表包含离线设备，则当“发布时在线的设备”全部下载完成时，
 * 额外推送一条通知（离线设备将在上线后继续下载）。最终仍以全部目标设备下载完成为发布成功。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPublishMessageTracker {

    private static final long TTL_DAYS = 14;

    private static final String KEY_META = "msg:publish:meta:%s";
    private static final String KEY_PENDING_ALL = "msg:publish:pending:all:%s";
    private static final String KEY_PENDING_ONLINE = "msg:publish:pending:online:%s";
    private static final String KEY_INDEX = "msg:publish:idx:%d:%d";
    private static final String KEY_NOTIFIED_ONLINE = "msg:publish:notified:online:%s";
    private static final String KEY_NOTIFIED_FINAL = "msg:publish:notified:final:%s";

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_PROGRAM_ID = "programId";
    private static final String FIELD_PROGRAM_NAME = "programName";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_RELEASE_PROGRAM_ID = "releaseProgramId";
    private static final String FIELD_TOTAL_TARGETS = "totalTargets";
    private static final String FIELD_TOTAL_ONLINE_TARGETS = "totalOnlineTargets";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final String MESSAGE_TYPE_PROGRAM_PUBLISH_FINISHED = "program.publish.finished";
    private static final String MESSAGE_TYPE_PROGRAM_PUBLISH_ONLINE_FINISHED = "program.publish.online.finished";

    private final StringRedisTemplate redisTemplate;
    private final MessageWriteApplicationService messageWriteApplicationService;

    /**
     * 启动一次节目发布追踪。
     *
     * @return publishOperationId（用于关联与排查）
     */
    public String start(UUID userId,
                        UUID programId,
                        String programName,
                        int version,
                        int releaseProgramId,
                        Collection<Long> targetDeviceIds,
                        Collection<Long> onlineDeviceIdsAtPublishTime) {
        if (userId == null || programId == null || releaseProgramId <= 0 || targetDeviceIds == null || targetDeviceIds.isEmpty()) {
            return null;
        }

        String publishOperationId = UUID.randomUUID().toString();

        Map<String, String> meta = new HashMap<>();
        meta.put(FIELD_USER_ID, userId.toString());
        meta.put(FIELD_PROGRAM_ID, programId.toString());
        meta.put(FIELD_PROGRAM_NAME, StringUtils.hasText(programName) ? programName.trim() : "");
        meta.put(FIELD_VERSION, String.valueOf(version));
        meta.put(FIELD_RELEASE_PROGRAM_ID, String.valueOf(releaseProgramId));
        meta.put(FIELD_TOTAL_TARGETS, String.valueOf(targetDeviceIds.size()));
        meta.put(FIELD_TOTAL_ONLINE_TARGETS, String.valueOf(onlineDeviceIdsAtPublishTime != null ? onlineDeviceIdsAtPublishTime.size() : 0));
        meta.put(FIELD_CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC).toString());

        String metaKey = metaKey(publishOperationId);
        redisTemplate.opsForHash().putAll(metaKey, meta);
        redisTemplate.expire(metaKey, TTL_DAYS, TimeUnit.DAYS);

        String pendingAllKey = pendingAllKey(publishOperationId);
        for (Long deviceId : targetDeviceIds) {
            if (deviceId == null || deviceId <= 0) {
                continue;
            }
            redisTemplate.opsForSet().add(pendingAllKey, String.valueOf(deviceId));
            redisTemplate.opsForValue().set(indexKey(deviceId, releaseProgramId), publishOperationId, TTL_DAYS, TimeUnit.DAYS);
        }
        redisTemplate.expire(pendingAllKey, TTL_DAYS, TimeUnit.DAYS);

        String pendingOnlineKey = pendingOnlineKey(publishOperationId);
        if (onlineDeviceIdsAtPublishTime != null && !onlineDeviceIdsAtPublishTime.isEmpty()) {
            for (Long deviceId : onlineDeviceIdsAtPublishTime) {
                if (deviceId == null || deviceId <= 0) {
                    continue;
                }
                redisTemplate.opsForSet().add(pendingOnlineKey, String.valueOf(deviceId));
            }
            redisTemplate.expire(pendingOnlineKey, TTL_DAYS, TimeUnit.DAYS);
        }

        return publishOperationId;
    }

    public void onDeviceDownloaded(UUID userId, Long deviceId, Integer releaseProgramId) {
        if (userId == null || deviceId == null || deviceId <= 0 || releaseProgramId == null || releaseProgramId <= 0) {
            return;
        }

        String publishOperationId = redisTemplate.opsForValue().get(indexKey(deviceId, releaseProgramId));
        if (!StringUtils.hasText(publishOperationId)) {
            return;
        }

        String metaKey = metaKey(publishOperationId);
        String pendingAllKey = pendingAllKey(publishOperationId);
        String pendingOnlineKey = pendingOnlineKey(publishOperationId);

        redisTemplate.opsForSet().remove(pendingAllKey, String.valueOf(deviceId));
        redisTemplate.opsForSet().remove(pendingOnlineKey, String.valueOf(deviceId));

        maybeNotifyOnlineFinished(publishOperationId, userId, metaKey, pendingOnlineKey, pendingAllKey);
        maybeNotifyFinalFinished(publishOperationId, userId, metaKey, pendingAllKey);
    }

    private void maybeNotifyOnlineFinished(String publishOperationId,
                                          UUID userId,
                                          String metaKey,
                                          String pendingOnlineKey,
                                          String pendingAllKey) {
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        long totalTargets = asLong(meta.get(FIELD_TOTAL_TARGETS));
        long totalOnlineTargets = asLong(meta.get(FIELD_TOTAL_ONLINE_TARGETS));
        if (totalTargets <= 0 || totalOnlineTargets <= 0) {
            return;
        }
        if (totalOnlineTargets >= totalTargets) {
            return;
        }

        Long remainingOnline = redisTemplate.opsForSet().size(pendingOnlineKey);
        if (remainingOnline == null || remainingOnline > 0) {
            return;
        }

        String notifiedKey = notifiedOnlineKey(publishOperationId);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(notifiedKey, "1", TTL_DAYS, TimeUnit.DAYS);
        if (first == null || !first) {
            return;
        }

        String programName = asString(meta.get(FIELD_PROGRAM_NAME));
        long offlineTargets = Math.max(0, totalTargets - totalOnlineTargets);
        int version = (int) asLong(meta.get(FIELD_VERSION));

        String title = StringUtils.hasText(programName)
            ? String.format("节目《%s》v%d 已在在线设备下载完成", programName, version)
            : String.format("节目 v%d 已在在线设备下载完成", version);
        String summary = String.format("在线设备 %d 台已完成下载，离线设备 %d 台将在上线后继续下载", totalOnlineTargets, offlineTargets);

        Map<String, Object> payload = new HashMap<>();
        payload.put("operationType", "PROGRAM_PUBLISH");
        payload.put("publishOperationId", publishOperationId);
        payload.put("programId", asString(meta.get(FIELD_PROGRAM_ID)));
        payload.put("programName", programName);
        payload.put("version", version);
        payload.put("releaseProgramId", asLong(meta.get(FIELD_RELEASE_PROGRAM_ID)));
        payload.put("totalTargets", totalTargets);
        payload.put("onlineTargets", totalOnlineTargets);
        payload.put("offlineTargets", offlineTargets);
        payload.put("pendingDeviceIds", limitMembers(pendingAllKey, 20));

        messageWriteApplicationService.createAndPublish(
            MessageKind.NOTIFICATION,
            MESSAGE_TYPE_PROGRAM_PUBLISH_ONLINE_FINISHED,
            MessageStatus.SUCCESS,
            userId,
            title,
            summary,
            payload,
            null,
            null,
            parseUuid(asString(meta.get(FIELD_PROGRAM_ID))),
            programName,
            publishOperationId,
            null
        );
    }

    private void maybeNotifyFinalFinished(String publishOperationId,
                                         UUID userId,
                                         String metaKey,
                                         String pendingAllKey) {
        Long remainingAll = redisTemplate.opsForSet().size(pendingAllKey);
        if (remainingAll == null || remainingAll > 0) {
            return;
        }

        String notifiedKey = notifiedFinalKey(publishOperationId);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(notifiedKey, "1", TTL_DAYS, TimeUnit.DAYS);
        if (first == null || !first) {
            return;
        }

        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        long totalTargets = asLong(meta.get(FIELD_TOTAL_TARGETS));
        String programName = asString(meta.get(FIELD_PROGRAM_NAME));
        int version = (int) asLong(meta.get(FIELD_VERSION));

        String title = StringUtils.hasText(programName)
            ? String.format("节目《%s》v%d 发布成功", programName, version)
            : String.format("节目 v%d 发布成功", version);
        String summary = String.format("共 %d 台设备已下载完成", totalTargets);

        Map<String, Object> payload = new HashMap<>();
        payload.put("operationType", "PROGRAM_PUBLISH");
        payload.put("publishOperationId", publishOperationId);
        payload.put("programId", asString(meta.get(FIELD_PROGRAM_ID)));
        payload.put("programName", programName);
        payload.put("version", version);
        payload.put("releaseProgramId", asLong(meta.get(FIELD_RELEASE_PROGRAM_ID)));
        payload.put("totalTargets", totalTargets);

        messageWriteApplicationService.createAndPublish(
            MessageKind.NOTIFICATION,
            MESSAGE_TYPE_PROGRAM_PUBLISH_FINISHED,
            MessageStatus.SUCCESS,
            userId,
            title,
            summary,
            payload,
            null,
            null,
            parseUuid(asString(meta.get(FIELD_PROGRAM_ID))),
            programName,
            publishOperationId,
            null
        );
    }

    private List<String> limitMembers(String key, int limit) {
        if (!StringUtils.hasText(key) || limit <= 0) {
            return List.of();
        }
        var members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().limit(limit).toList();
    }

    private String metaKey(String publishOperationId) {
        return String.format(KEY_META, publishOperationId);
    }

    private String pendingAllKey(String publishOperationId) {
        return String.format(KEY_PENDING_ALL, publishOperationId);
    }

    private String pendingOnlineKey(String publishOperationId) {
        return String.format(KEY_PENDING_ONLINE, publishOperationId);
    }

    private String indexKey(Long deviceId, int releaseProgramId) {
        return String.format(KEY_INDEX, deviceId, releaseProgramId);
    }

    private String notifiedOnlineKey(String publishOperationId) {
        return String.format(KEY_NOTIFIED_ONLINE, publishOperationId);
    }

    private String notifiedFinalKey(String publishOperationId) {
        return String.format(KEY_NOTIFIED_FINAL, publishOperationId);
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

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ignore) {
            return null;
        }
    }
}
