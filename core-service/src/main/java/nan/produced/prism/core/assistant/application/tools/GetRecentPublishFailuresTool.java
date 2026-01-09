package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.message.domain.MessageEntity;
import nan.produced.prism.core.message.domain.MessageStatus;
import nan.produced.prism.core.message.infrastructure.persistence.MessageRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetRecentPublishFailuresTool implements AssistantTool {

    private static final String MESSAGE_TYPE_DEVICE_COMMAND_FINISHED = "device.command.finished";

    private final ObjectMapper objectMapper;
    private final MessageRepositoryJpa messageRepositoryJpa;

    @Override
    public String name() {
        return "getRecentPublishFailures";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        int sinceMinutes = intOrDefault(input, "sinceMinutes", 24 * 60);
        sinceMinutes = Math.max(1, Math.min(sinceMinutes, 30 * 24 * 60));

        int limit = intOrDefault(input, "limit", 50);
        limit = Math.max(1, Math.min(limit, 100));

        String actionTypePrefix = textOrNull(input, "actionTypePrefix");
        if (!StringUtils.hasText(actionTypePrefix)) {
            actionTypePrefix = "PROGRAM";
        }
        String normalizedPrefix = actionTypePrefix.trim().toUpperCase(Locale.ROOT);

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(sinceMinutes);

        List<MessageEntity> candidates = messageRepositoryJpa.findAll(spec(userId, cutoff), PageRequest.of(
                0,
                limit * 3, // fetch extra, we will filter by payload.actionType
                Sort.by(Sort.Direction.DESC, "createdAt")
        )).getContent();

        List<ObjectNode> items = new ArrayList<>();
        for (MessageEntity m : candidates) {
            if (m == null) {
                continue;
            }
            JsonNode payloadNode = parsePayload(m.getPayload());
            String actionType = textAt(payloadNode, "actionType");
            if (!StringUtils.hasText(actionType) || !actionType.trim().toUpperCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                continue;
            }

            ObjectNode row = objectMapper.createObjectNode();
            row.put("messageId", m.getId() != null ? m.getId().toString() : null);
            row.put("deviceId", m.getDeviceId() != null ? String.valueOf(m.getDeviceId()) : null);
            row.put("deviceName", m.getDeviceNameSnapshot());
            row.put("actionType", actionType);
            row.put("finalStatus", textAt(payloadNode, "finalStatus"));
            row.put("errorMessage", textAt(payloadNode, "errorMessage"));
            row.put("operationId", m.getOperationId() != null ? String.valueOf(m.getOperationId()) : null);
            row.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            items.add(row);

            if (items.size() >= limit) {
                break;
            }
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("sinceMinutes", sinceMinutes);
        out.put("limit", limit);
        out.put("actionTypePrefix", normalizedPrefix);
        out.put("count", items.size());
        out.set("items", objectMapper.valueToTree(items));
        out.put("definition", "Failures are derived from device.command.finished messages with FAILED status (e.g., EXPIRED).");
        return out;
    }

    private Specification<MessageEntity> spec(UUID userId, OffsetDateTime cutoff) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("userId"), userId));
            ps.add(cb.equal(root.get("type"), MESSAGE_TYPE_DEVICE_COMMAND_FINISHED));
            ps.add(cb.equal(root.get("status"), MessageStatus.FAILED));
            ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), cutoff));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private JsonNode parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("_raw", payload);
            return node;
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

    private static String textAt(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }
}
