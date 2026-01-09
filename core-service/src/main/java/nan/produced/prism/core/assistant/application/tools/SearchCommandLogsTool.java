package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceCommandLogRepositoryJpa;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SearchCommandLogsTool implements AssistantTool {

    private static final int DEFAULT_SINCE_MINUTES = 1440;
    private static final int MAX_SINCE_MINUTES = 43200;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final ObjectMapper objectMapper;
    private final DeviceCommandLogRepositoryJpa commandLogRepositoryJpa;
    private final DeviceRepositoryJpa deviceRepositoryJpa;

    @Override
    public String name() {
        return "searchCommandLogs";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        String deviceName = textOrNull(input, "deviceName");
        Long deviceId = longOrNull(input, "deviceId");
        String actionTypeRaw = textOrNull(input, "actionType");
        List<String> statusesRaw = stringListOrEmpty(input, "statuses");
        Boolean accepted = booleanOrNull(input, "accepted");
        Boolean covered = booleanOrNull(input, "covered");
        String sendMethod = textOrNull(input, "sendMethod");

        int sinceMinutes = intOrDefault(input, "sinceMinutes", DEFAULT_SINCE_MINUTES);
        sinceMinutes = Math.max(1, Math.min(MAX_SINCE_MINUTES, sinceMinutes));

        int limit = intOrDefault(input, "limit", DEFAULT_LIMIT);
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        DeviceActionType actionType = parseActionTypeOrNull(actionTypeRaw);
        List<DeviceCommandStatus> statuses = parseStatuses(statusesRaw);

        List<Long> deviceIds = resolveDeviceIds(userId, deviceId, deviceName);
        if (deviceIds != null && deviceIds.isEmpty()) {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("count", 0);
            out.set("items", objectMapper.createArrayNode());
            out.put("hint", "未找到匹配设备，请确认设备名称是否正确。");
            return out;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusMinutes(sinceMinutes);

        final List<Long> deviceIdsFinal = deviceIds;
        final DeviceActionType actionTypeFinal = actionType;
        final List<DeviceCommandStatus> statusesFinal = statuses;
        final String sendMethodFinal = StringUtils.hasText(sendMethod) ? sendMethod.trim().toLowerCase(Locale.ROOT) : null;

        Specification<DeviceCommandLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), now));

            if (deviceIdsFinal != null) {
                predicates.add(root.get("deviceId").in(deviceIdsFinal));
            }
            if (actionTypeFinal != null) {
                predicates.add(cb.equal(root.get("actionType"), actionTypeFinal));
            }
            if (statusesFinal != null && !statusesFinal.isEmpty()) {
                predicates.add(root.get("status").in(statusesFinal));
            }
            if (accepted != null) {
                predicates.add(cb.equal(root.get("accepted"), accepted));
            }
            if (covered != null) {
                predicates.add(cb.equal(root.get("covered"), covered));
            }
            if (sendMethodFinal != null) {
                predicates.add(cb.equal(cb.lower(root.get("sendMethod")), sendMethodFinal));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        var pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        List<DeviceCommandLog> logs = commandLogRepositoryJpa.findAll(spec, pageable).getContent();

        Set<Long> ids = logs.stream()
                .map(DeviceCommandLog::getDeviceId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        Map<Long, String> names = ids.isEmpty() ? Map.of() : deviceRepositoryJpa.findByUserIdAndDeviceIdIn(userId, ids).stream()
                .filter(d -> d != null && d.getDeviceId() != null)
                .collect(Collectors.toMap(
                        DeviceEntity::getDeviceId,
                        d -> StringUtils.hasText(d.getDeviceName()) ? d.getDeviceName().trim() : ("Device " + d.getDeviceId()),
                        (a, b) -> a,
                        HashMap::new
                ));

        ObjectNode out = objectMapper.createObjectNode();
        out.put("count", logs.size());
        out.put("sinceMinutes", sinceMinutes);
        out.put("limit", limit);
        out.put("hint", "请选择一条指令继续排查（前端可把每条记录渲染为按钮，点击后发送 [[commandLogId:...]]）。");
        out.set("items", objectMapper.valueToTree(logs.stream().map(log -> {
            Map<String, Object> row = new HashMap<>();
            row.put("commandLogId", log.getId() != null ? String.valueOf(log.getId()) : null);
            row.put("deviceId", log.getDeviceId() != null ? String.valueOf(log.getDeviceId()) : null);
            row.put("deviceName", names.getOrDefault(log.getDeviceId(), "Device " + log.getDeviceId()));
            row.put("actionType", log.getActionType() != null ? log.getActionType().name() : null);
            row.put("status", log.getStatus() != null ? log.getStatus().name() : null);
            row.put("accepted", log.isAccepted());
            row.put("covered", log.isCovered());
            row.put("sendMethod", log.getSendMethod());
            row.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            return row;
        }).toList()));
        return out;
    }

    private List<Long> resolveDeviceIds(UUID userId, Long deviceId, String deviceName) {
        if (deviceId != null && deviceId > 0) {
            DeviceEntity d = deviceRepositoryJpa.findByDeviceIdAndUserId(deviceId, userId);
            return d != null ? List.of(deviceId) : List.of();
        }
        if (StringUtils.hasText(deviceName)) {
            List<DeviceEntity> matches = deviceRepositoryJpa.searchForUnifiedSearch(userId, deviceName.trim(), 20);
            if (matches == null || matches.isEmpty()) {
                return List.of();
            }
            return matches.stream()
                    .map(DeviceEntity::getDeviceId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }
        return null;
    }

    private static List<DeviceCommandStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<DeviceCommandStatus> out = new ArrayList<>();
        for (String s : raw) {
            if (!StringUtils.hasText(s)) {
                continue;
            }
            try {
                out.add(DeviceCommandStatus.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    private static DeviceActionType parseActionTypeOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return DeviceActionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
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

    private static Boolean booleanOrNull(JsonNode input, String field) {
        if (input == null) {
            return null;
        }
        JsonNode v = input.get(field);
        if (v == null) {
            return null;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isInt()) {
            return v.asInt() != 0;
        }
        if (v.isTextual()) {
            String s = v.asText().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
                return false;
            }
        }
        return null;
    }

    private static List<String> stringListOrEmpty(JsonNode input, String field) {
        if (input == null) {
            return List.of();
        }
        JsonNode v = input.get(field);
        if (v == null) {
            return List.of();
        }
        if (v.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode item : v) {
                if (item != null && item.isTextual() && StringUtils.hasText(item.asText())) {
                    out.add(item.asText());
                }
            }
            return out;
        }
        if (v.isTextual() && StringUtils.hasText(v.asText())) {
            return List.of(v.asText());
        }
        return List.of();
    }
}
