package nan.produced.prism.core.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.api.dto.DeviceDetailResp;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiagnoseDeviceTool implements AssistantTool {

    private final ObjectMapper objectMapper;
    private final DeviceSearchUseCase deviceSearchUseCase;

    @Override
    public String name() {
        return "diagnoseDevice";
    }

    @Override
    public JsonNode execute(UUID userId, JsonNode input) {
        Long deviceId = longOrNull(input, "deviceId");
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("deviceId is required");
        }

        DeviceDetailResp d = deviceSearchUseCase.getDeviceDetail(userId, deviceId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean online = d.getOnlineStatus() != null && d.getOnlineStatus() == 1;

        Long total = d.getTotalStorage();
        Long free = d.getFreeStorage();
        Double freeRatio = (total != null && total > 0 && free != null && free >= 0) ? (free * 1.0 / total) : null;

        long lastSeenMinutes = -1;
        if (d.getLastReportTime() != null) {
            lastSeenMinutes = Duration.between(d.getLastReportTime(), now).toMinutes();
        }

        List<String> issues = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (!online) {
            issues.add("设备当前离线");
            actions.add("检查设备电源与网络连接（路由/交换机/4G 信号）");
            actions.add("确认设备是否在省电/休眠状态");
        }
        if (lastSeenMinutes >= 0 && lastSeenMinutes > 30) {
            issues.add("设备上报延迟较大（" + lastSeenMinutes + " 分钟未上报）");
            actions.add("检查设备到云端的网络是否阻断（防火墙/代理/DNS）");
        }
        if (freeRatio != null && freeRatio < 0.10) {
            issues.add("设备存储空间偏低（剩余约 " + Math.round(freeRatio * 100) + "%）");
            actions.add("清理媒体库或删除不再使用的节目资源后重试发布");
        }
        if (d.getNetworkStrength() != null && d.getNetworkStrength() < 20) {
            issues.add("4G 信号较弱（" + d.getNetworkStrength() + "）");
            actions.add("调整设备位置或更换网络环境");
        }

        if (issues.isEmpty()) {
            issues.add("未发现明显异常");
        }
        if (actions.isEmpty()) {
            actions.add("如仍有问题，请提供 deviceId + 发生时间点以便进一步分析");
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("deviceId", d.getDeviceId() != null ? String.valueOf(d.getDeviceId()) : null);
        out.put("deviceName", d.getDeviceName());
        out.put("onlineStatus", d.getOnlineStatus());
        out.put("lastReportTime", d.getLastReportTime() != null ? d.getLastReportTime().toString() : null);
        out.put("onboardingTime", d.getOnboardingTime() != null ? d.getOnboardingTime().toString() : null);
        out.put("model", d.getModel());
        out.put("version", d.getVersion());
        out.put("networkType", d.getNetworkType() != null ? d.getNetworkType().name() : null);
        out.put("networkStrength", d.getNetworkStrength());
        out.put("powerStatus", d.getPowerStatus());
        out.put("playingProgram", d.getPlayingProgram());
        out.put("resolution", d.getResolution());
        out.put("totalStorage", d.getTotalStorage());
        out.put("freeStorage", d.getFreeStorage());
        out.put("lastSeenMinutes", lastSeenMinutes >= 0 ? lastSeenMinutes : null);
        out.put("freeStorageRatio", freeRatio);
        out.set("issues", objectMapper.valueToTree(issues));
        out.set("recommendedActions", objectMapper.valueToTree(actions));
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
