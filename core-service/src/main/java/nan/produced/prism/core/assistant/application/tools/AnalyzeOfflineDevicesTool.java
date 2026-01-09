package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnalyzeOfflineDevicesTool implements AssistantTool {

    private final ObjectMapper objectMapper;
    private final DeviceSearchUseCase deviceSearchUseCase;

    @Override
    public String name() {
        return "analyzeOfflineDevices";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        int limit = intOrDefault(input, "limit", 50);
        limit = Math.max(1, Math.min(limit, 200));

        List<DeviceListVO> all = deviceSearchUseCase.listAllUsersDevices(userId);
        List<DeviceListVO> offline = all == null ? List.of() : all.stream()
                .filter(d -> d.getOnlineStatus() != null && d.getOnlineStatus() == 0)
                .limit(limit)
                .toList();

        ObjectNode out = objectMapper.createObjectNode();
        out.put("offlineCount", offline.size());
        out.put("limit", limit);
        out.set("items", objectMapper.valueToTree(offline.stream().map(d -> {
            ObjectNode row = objectMapper.createObjectNode();
            row.put("deviceId", d.getDeviceId() != null ? String.valueOf(d.getDeviceId()) : null);
            row.put("deviceName", d.getDeviceName());
            row.put("lastReportTime", d.getLastReportTime() != null ? d.getLastReportTime().toString() : null);
            row.put("model", d.getModel());
            return row;
        }).toList()));
        return out;
    }

    private static int intOrDefault(JsonNode input, String field, int defaultValue) {
        if (input == null) {
            return defaultValue;
        }
        JsonNode v = input.get(field);
        return v != null && v.isInt() ? v.asInt() : defaultValue;
    }
}
