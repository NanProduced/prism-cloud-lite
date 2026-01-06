package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.command.DeviceActionTrackingLevel;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceCommandLogRepositoryJpa;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiagnoseDeviceCommandTool implements AssistantTool {

    private static final int DEFAULT_SINCE_MINUTES = 1440;
    private static final int MAX_SINCE_MINUTES = 43200;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final String COMMAND_LISTENER_KEY = "command:listener:%d:%s";

    private final ObjectMapper objectMapper;
    private final DeviceCommandLogRepositoryJpa commandLogRepositoryJpa;
    private final DeviceRepositoryJpa deviceRepositoryJpa;
    private final StringRedisTemplate redisTemplate;

    @Override
    public String name() {
        return "diagnoseDeviceCommand";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        Long commandLogId = longOrNull(input, "commandLogId");
        String operationId = textOrNull(input, "operationId");
        Long deviceId = longOrNull(input, "deviceId");
        String deviceName = textOrNull(input, "deviceName");
        String actionTypeRaw = textOrNull(input, "actionType");

        int sinceMinutes = intOrDefault(input, "sinceMinutes", DEFAULT_SINCE_MINUTES);
        sinceMinutes = Math.max(1, Math.min(MAX_SINCE_MINUTES, sinceMinutes));

        int limit = intOrDefault(input, "limit", DEFAULT_LIMIT);
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        DeviceCommandLog resolved = resolveOne(userId, commandLogId, operationId);

        List<DeviceCommandLog> candidates = null;
        if (resolved == null) {
            DeviceActionType actionType = parseActionTypeOrNull(actionTypeRaw);
            candidates = findCandidates(userId, deviceId, deviceName, actionType, sinceMinutes, limit);
            if (candidates.isEmpty()) {
                ObjectNode out = objectMapper.createObjectNode();
                out.put("found", false);
                out.put("summary", "没有找到可排查的指令记录（在最近 " + sinceMinutes + " 分钟内）。");
                out.set("suggestedNextQuestions", objectMapper.valueToTree(List.of(
                        "这条指令大概是什么时候下发的？（例如：10 分钟前 / 今天上午）",
                        "是哪台设备？（设备名称）",
                        "动作类型是什么？（例如：截图 / 重启 / 发布）"
                )));
                return out;
            }
            resolved = candidates.get(0);
        }

        if (resolved == null || resolved.getDeviceId() == null) {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("found", false);
            out.put("summary", "未找到指令记录或无权访问。");
            return out;
        }

        DeviceEntity device = deviceRepositoryJpa.findByDeviceIdAndUserId(resolved.getDeviceId(), userId);
        Map<String, Object> diagnosis = diagnose(resolved, device);

        ObjectNode out = objectMapper.createObjectNode();
        out.put("found", true);
        out.put("summary", (String) diagnosis.get("summary"));
        out.set("device", objectMapper.valueToTree(diagnosis.get("device")));
        out.set("command", objectMapper.valueToTree(diagnosis.get("command")));
        out.set("analysis", objectMapper.valueToTree(diagnosis.get("analysis")));

        if (candidates != null && candidates.size() > 1) {
            out.set("candidates", objectMapper.valueToTree(candidates.stream()
                    .limit(10)
                    .map(this::toCandidateItem)
                    .toList()));
        }
        return out;
    }

    private DeviceCommandLog resolveOne(UUID userId, Long commandLogId, String operationId) {
        if (commandLogId != null && commandLogId > 0) {
            return commandLogRepositoryJpa.findByIdAndUserId(commandLogId, userId).orElse(null);
        }
        if (StringUtils.hasText(operationId)) {
            UUID op;
            try {
                op = UUID.fromString(operationId.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            DeviceCommandLog log = commandLogRepositoryJpa.findByOperationId(op).orElse(null);
            if (log == null || log.getUserId() == null || !log.getUserId().equals(userId)) {
                return null;
            }
            return log;
        }
        return null;
    }

    private List<DeviceCommandLog> findCandidates(UUID userId,
                                                 Long deviceId,
                                                 String deviceName,
                                                 DeviceActionType actionType,
                                                 int sinceMinutes,
                                                 int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusMinutes(sinceMinutes);

        List<Long> deviceIds = null;
        if (deviceId != null && deviceId > 0) {
            deviceIds = List.of(deviceId);
        } else if (StringUtils.hasText(deviceName)) {
            List<DeviceEntity> matches = deviceRepositoryJpa.searchForUnifiedSearch(userId, deviceName.trim(), 20);
            if (matches != null && !matches.isEmpty()) {
                deviceIds = matches.stream()
                        .map(DeviceEntity::getDeviceId)
                        .filter(id -> id != null && id > 0)
                        .toList();
            } else {
                deviceIds = List.of();
            }
        }

        final List<Long> deviceIdsFinal = deviceIds;
        final DeviceActionType actionTypeFinal = actionType;
        Specification<DeviceCommandLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), now));
            if (deviceIdsFinal != null) {
                if (deviceIdsFinal.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("deviceId").in(deviceIdsFinal));
                }
            }
            if (actionTypeFinal != null) {
                predicates.add(cb.equal(root.get("actionType"), actionTypeFinal));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return commandLogRepositoryJpa.findAll(spec, pageable).getContent();
    }

    private ObjectNode toCandidateItem(DeviceCommandLog log) {
        ObjectNode row = objectMapper.createObjectNode();
        row.put("commandLogId", log.getId());
        row.put("deviceId", log.getDeviceId());
        row.put("actionType", log.getActionType() != null ? log.getActionType().name() : null);
        row.put("status", log.getStatus() != null ? log.getStatus().name() : null);
        row.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return row;
    }

    private Map<String, Object> diagnose(DeviceCommandLog log, DeviceEntity device) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        boolean online = device != null && device.getOnlineStatus() != null && device.getOnlineStatus() == 1;
        Long deviceId = log.getDeviceId();
        String deviceName = device != null && StringUtils.hasText(device.getDeviceName())
                ? device.getDeviceName().trim()
                : "Device " + deviceId;

        long ageMinutes = -1;
        if (log.getCreatedAt() != null) {
            ageMinutes = Duration.between(log.getCreatedAt(), now).toMinutes();
        }

        String sendMethod = log.getSendMethod();
        String sendMethodNorm = sendMethod != null ? sendMethod.trim().toLowerCase(Locale.ROOT) : "";
        boolean sendByHttpCache = sendMethodNorm.contains("http");
        boolean sendByWebsocket = sendMethodNorm.contains("websocket") || sendMethodNorm.contains("ws");

        DeviceCommandStatus status = log.getStatus();
        DeviceActionTrackingLevel trackingLevel = log.getTrackingLevel();

        boolean trackingWindowActive = false;
        if (trackingLevel == DeviceActionTrackingLevel.PROPERTY_MATCH
                && status == DeviceCommandStatus.CONFIRMED
                && log.getActionType() != null
                && deviceId != null) {
            String key = String.format(COMMAND_LISTENER_KEY, deviceId, log.getActionType().name());
            Boolean exists = redisTemplate.hasKey(key);
            trackingWindowActive = Boolean.TRUE.equals(exists);
        }

        List<String> likelyCauses = new ArrayList<>();
        List<String> recommendedActions = new ArrayList<>();
        String state;
        String summary;

        if (!log.isAccepted() || status == DeviceCommandStatus.FAILED) {
            state = "REJECTED";
            summary = "指令下发失败（系统未接受该指令）。";
            if (StringUtils.hasText(log.getErrorMessage())) {
                likelyCauses.add("下发失败原因：" + log.getErrorMessage().trim());
            } else {
                likelyCauses.add("下发时被拒绝（可能是参数不合法、设备状态不允许或权限不足）");
            }
            recommendedActions.add("确认设备当前状态与权限是否允许执行该动作（例如：设备离线/休眠）");
            recommendedActions.add("重试一次；若持续失败，请截图错误提示并联系支持");
        } else if (status == DeviceCommandStatus.PUBLISHED) {
            state = "PENDING";
            summary = "指令已发出，但设备尚未确认收到。";
            if (!online) {
                likelyCauses.add("设备当前离线，无法及时接收指令");
                recommendedActions.add("先让设备上线（电源/网络/设备登录）后再重试");
            } else {
                likelyCauses.add("设备在线但仍未确认收到，可能存在网络抖动或连接不稳定");
                recommendedActions.add("稍等 1-2 分钟后再查看；或重试下发一次");
            }
            if (sendByHttpCache) {
                likelyCauses.add("设备需要在下次与云端同步时才会领取并执行该指令");
                recommendedActions.add("等待设备同步后再检查结果（可先看设备“最后上报时间”是否更新）");
            } else if (sendByWebsocket) {
                likelyCauses.add("设备需要保持在线连接以接收实时指令");
                recommendedActions.add("确认设备网络无阻断（防火墙/代理/DNS）");
            }
            if (log.isCovered()) {
                likelyCauses.add("该指令可能已被同类型的新指令覆盖，设备只会执行最新一次");
                recommendedActions.add("检查最近一次同类型指令的状态，或重新下发最新指令");
            }
        } else if (status == DeviceCommandStatus.CONFIRMED) {
            if (trackingLevel == DeviceActionTrackingLevel.PROPERTY_MATCH) {
                state = "DELIVERED_WAITING_RESULT";
                if (trackingWindowActive) {
                    summary = "设备已确认收到指令，正在等待设备上报执行结果。";
                    likelyCauses.add("设备已收到但尚未上报与该动作相关的状态变化");
                    recommendedActions.add("等待设备上报（通常几秒到几十秒），然后刷新查看");
                    if (!online) {
                        likelyCauses.add("设备当前离线，可能无法继续上报结果");
                        recommendedActions.add("先恢复设备在线，再重新触发一次该动作");
                    }
                } else {
                    summary = "设备已确认收到指令，但未观察到执行结果（可能已超出可追踪窗口）。";
                    likelyCauses.add("设备没有按预期上报状态变化，或上报延迟超过追踪窗口");
                    recommendedActions.add("重新下发一次该动作，并在设备在线时观察结果");
                    recommendedActions.add("检查设备上报是否正常（最后上报时间是否持续更新）");
                }
            } else {
                state = "DELIVERED";
                summary = "设备已确认收到指令。该动作类型不要求额外的执行回执。";
                likelyCauses.add("设备确认收到后即视为本次操作结束；实际效果请以设备状态/截图为准");
                recommendedActions.add("刷新设备状态或截图确认结果");
                if (!online) {
                    recommendedActions.add("若设备随后离线，可能无法立刻体现效果；先恢复设备在线");
                }
            }
            if (log.isCovered()) {
                likelyCauses.add("该指令可能已被同类型的新指令覆盖，设备只会执行最新一次");
                recommendedActions.add("检查最新一次同类型指令的状态");
            }
        } else if (status == DeviceCommandStatus.COMPLETED) {
            state = "COMPLETED";
            summary = "指令已完成。";
            recommendedActions.add("如你仍未看到效果，请刷新设备状态/截图，并确认是否有排程覆盖");
        } else if (status == DeviceCommandStatus.EXPIRED) {
            state = "EXPIRED";
            summary = "指令投递超时（设备未在有效期内确认收到）。";
            if (!online) {
                likelyCauses.add("设备在有效期内未上线或连接不稳定");
            } else {
                likelyCauses.add("设备在线但未在有效期内确认收到，可能网络不通或设备端异常");
            }
            recommendedActions.add("让设备保持在线后重试该动作");
            recommendedActions.add("如频繁超时，请排查网络策略（防火墙/代理/DNS）");
        } else {
            state = status != null ? status.name() : "UNKNOWN";
            summary = "指令状态：" + state;
            recommendedActions.add("请提供更具体的设备与时间范围以便进一步分析");
        }

        ObjectNode deviceNode = objectMapper.createObjectNode();
        deviceNode.put("deviceId", deviceId);
        deviceNode.put("deviceName", deviceName);
        deviceNode.put("online", online);
        deviceNode.put("lastReportTime", device != null && device.getLastReportTime() != null ? device.getLastReportTime().toString() : null);

        ObjectNode commandNode = objectMapper.createObjectNode();
        commandNode.put("commandLogId", log.getId());
        commandNode.put("actionType", log.getActionType() != null ? log.getActionType().name() : null);
        commandNode.put("trackingLevel", trackingLevel != null ? trackingLevel.name() : null);
        commandNode.put("status", status != null ? status.name() : null);
        commandNode.put("accepted", log.isAccepted());
        commandNode.put("covered", log.isCovered());
        commandNode.put("sendMethod", log.getSendMethod());
        commandNode.put("queuedId", log.getQueuedId());
        commandNode.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        commandNode.put("updatedAt", log.getUpdatedAt() != null ? log.getUpdatedAt().toString() : null);
        if (ageMinutes >= 0) {
            commandNode.put("ageMinutes", ageMinutes);
        } else {
            commandNode.putNull("ageMinutes");
        }
        if (trackingLevel == DeviceActionTrackingLevel.PROPERTY_MATCH) {
            commandNode.put("trackingWindowActive", trackingWindowActive);
        } else {
            commandNode.putNull("trackingWindowActive");
        }

        ObjectNode analysisNode = objectMapper.createObjectNode();
        analysisNode.put("state", state);
        analysisNode.set("likelyCauses", objectMapper.valueToTree(likelyCauses));
        analysisNode.set("recommendedActions", objectMapper.valueToTree(recommendedActions));

        return Map.of(
                "summary", summary,
                "device", deviceNode,
                "command", commandNode,
                "analysis", analysisNode
        );
    }

    private static DeviceActionType parseActionTypeOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return DeviceActionType.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String textOrNull(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode v = input.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static int intOrDefault(JsonNode input, String field, int defaultValue) {
        if (input == null) {
            return defaultValue;
        }
        JsonNode v = input.get(field);
        return v != null && v.isInt() ? v.asInt() : defaultValue;
    }

    private static Long longOrNull(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode v = input.get(field);
        if (v == null) {
            return null;
        }
        if (v.isLong() || v.isInt()) {
            return v.asLong();
        }
        if (v.isTextual()) {
            try {
                return Long.parseLong(v.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
