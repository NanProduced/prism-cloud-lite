package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.api.dto.DeviceDetailResp;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeviceDetailTool implements AssistantTool {

    private final ObjectMapper objectMapper;
    private final DeviceSearchUseCase deviceSearchUseCase;

    @Override
    public String name() {
        return "getDeviceDetail";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        Long deviceId = longOrNull(input, "deviceId");
        if (deviceId == null) {
            throw new IllegalArgumentException("deviceId is required");
        }

        DeviceDetailResp d = deviceSearchUseCase.getDeviceDetail(userId, deviceId);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("deviceId", d.getDeviceId() != null ? String.valueOf(d.getDeviceId()) : null);
        out.put("deviceName", d.getDeviceName());
        out.put("onlineStatus", d.getOnlineStatus());
        out.put("model", d.getModel());
        out.put("version", d.getVersion());
        out.put("lastReportTime", d.getLastReportTime() != null ? d.getLastReportTime().toString() : null);
        out.put("playingProgram", d.getPlayingProgram());
        out.put("resolution", d.getResolution());
        out.put("totalStorage", d.getTotalStorage());
        out.put("freeStorage", d.getFreeStorage());
        return out;
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
