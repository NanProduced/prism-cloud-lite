package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchItem;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeviceSearchTool implements AssistantTool {

    private final ObjectMapper objectMapper;
    private final DeviceSearchUseCase deviceSearchUseCase;

    @Override
    public String name() {
        return "searchDevices";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        String keyword = textOrNull(input, "keyword");
        int limit = intOrDefault(input, "limit", 20);
        limit = Math.max(1, Math.min(limit, 50));

        List<DeviceSearchItem> items = deviceSearchUseCase.searchDevices(userId, keyword, limit);

        ObjectNode out = objectMapper.createObjectNode();
        out.put("keyword", keyword);
        out.put("count", items != null ? items.size() : 0);
        out.set("items", objectMapper.valueToTree(items == null ? List.of() : items.stream().map(it -> {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("id", it.id());
            row.put("name", it.name());
            row.put("serialNo", it.serialNo());
            row.put("onlineStatus", it.onlineStatus());
            row.put("model", it.model());
            return row;
        }).toList()));
        return out;
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
}

