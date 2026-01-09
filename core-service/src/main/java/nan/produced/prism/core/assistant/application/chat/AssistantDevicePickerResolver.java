package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Heuristic resolver: when the user asks "check my device status" without specifying a device,
 * return a candidate list for frontend interactive selection.
 */
@Component
@RequiredArgsConstructor
public class AssistantDevicePickerResolver {

    private static final int MAX_CANDIDATES = 12;
    private static final Pattern HAS_ID_PATTERN = Pattern.compile("\\b\\d{3,}\\b");

    private final DeviceSearchUseCase deviceSearchUseCase;

    public record PickDeviceItem(String deviceId, String label, boolean online, String lastReportTime) {
    }

    public record PickPayload(String title, String hint, List<PickDeviceItem> items, boolean includeFleetOption) {
    }

    public PickPayload resolve(UUID userId, String userText) {
        if (userId == null || !StringUtils.hasText(userText)) {
            return null;
        }
        String text = userText.trim();
        if (!shouldTrigger(text)) {
            return null;
        }

        List<DeviceListVO> all = deviceSearchUseCase.listAllUsersDevices(userId);
        if (all == null || all.isEmpty()) {
            return null;
        }

        List<DeviceListVO> sorted = new ArrayList<>(all);
        sorted.sort(Comparator
                .comparing((DeviceListVO d) -> d != null && d.getOnlineStatus() != null && d.getOnlineStatus() == 1, Comparator.reverseOrder())
                .thenComparing(d -> {
                    OffsetDateTime t = d != null ? d.getLastReportTime() : null;
                    return t != null ? t : OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
                }, Comparator.reverseOrder())
        );

        List<PickDeviceItem> items = new ArrayList<>();
        for (DeviceListVO d : sorted) {
            if (d == null || d.getDeviceId() == null) {
                continue;
            }
            if (items.size() >= MAX_CANDIDATES) {
                break;
            }
            boolean online = d.getOnlineStatus() != null && d.getOnlineStatus() == 1;
            String label = StringUtils.hasText(d.getDeviceName()) ? d.getDeviceName().trim() : ("Device " + d.getDeviceId());
            String last = d.getLastReportTime() != null ? d.getLastReportTime().toString() : null;
            items.add(new PickDeviceItem(String.valueOf(d.getDeviceId()), label, online, last));
        }

        if (items.isEmpty()) {
            return null;
        }

        return new PickPayload(
                "请选择要查看的设备（或查看整体概览）",
                "你也可以直接说“看整体”，或输入设备名称进行搜索。",
                List.copyOf(items),
                true
        );
    }

    private static boolean shouldTrigger(String userText) {
        String lowered = userText.toLowerCase(Locale.ROOT);
        // If user already specifies an ID, don't interrupt.
        if (HAS_ID_PATTERN.matcher(lowered).find()) {
            return false;
        }
        boolean mentionsDevice = lowered.contains("设备") || lowered.contains("device");
        if (!mentionsDevice) {
            return false;
        }
        // Trigger on common "status/health/situation" intents.
        boolean statusIntent = lowered.contains("情况")
                || lowered.contains("状态")
                || lowered.contains("在线")
                || lowered.contains("离线")
                || lowered.contains("怎么样")
                || lowered.contains("怎么了")
                || lowered.contains("看看")
                || lowered.contains("check")
                || lowered.contains("status")
                || lowered.contains("health");

        // Also trigger on "query device info/detail/params" intents (common in the UI).
        boolean queryIntent = lowered.contains("查询")
                || lowered.contains("查看")
                || lowered.contains("获取")
                || lowered.contains("检索")
                || lowered.contains("search")
                || lowered.contains("get");
        boolean infoIntent = lowered.contains("信息")
                || lowered.contains("详情")
                || lowered.contains("参数")
                || lowered.contains("配置")
                || lowered.contains("运行")
                || lowered.contains("硬件")
                || lowered.contains("detail")
                || lowered.contains("info")
                || lowered.contains("param")
                || lowered.contains("config");

        return statusIntent || (queryIntent && infoIntent);
    }
}
