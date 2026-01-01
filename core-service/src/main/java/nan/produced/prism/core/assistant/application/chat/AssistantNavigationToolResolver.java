package nan.produced.prism.core.assistant.application.chat;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class AssistantNavigationToolResolver {

    public record NavigationTarget(String path, String label) {
    }

    private final Map<String, NavigationTarget> keywordRoutes = new LinkedHashMap<>();

    public AssistantNavigationToolResolver() {
        keywordRoutes.put("device", new NavigationTarget("/dashboard/devices", "设备列表"));
        keywordRoutes.put("设备", new NavigationTarget("/dashboard/devices", "设备列表"));
        keywordRoutes.put("media", new NavigationTarget("/dashboard/media", "素材库"));
        keywordRoutes.put("素材", new NavigationTarget("/dashboard/media", "素材库"));
        keywordRoutes.put("program", new NavigationTarget("/dashboard/programs", "节目"));
        keywordRoutes.put("节目", new NavigationTarget("/dashboard/programs", "节目"));
        keywordRoutes.put("schedule", new NavigationTarget("/dashboard/schedule", "排期"));
        keywordRoutes.put("排期", new NavigationTarget("/dashboard/schedule", "排期"));
        keywordRoutes.put("monitor", new NavigationTarget("/dashboard/monitoring", "监控"));
        keywordRoutes.put("监控", new NavigationTarget("/dashboard/monitoring", "监控"));
        keywordRoutes.put("map", new NavigationTarget("/dashboard/map", "地图"));
        keywordRoutes.put("地图", new NavigationTarget("/dashboard/map", "地图"));
        keywordRoutes.put("settings", new NavigationTarget("/dashboard/settings", "设置"));
        keywordRoutes.put("设置", new NavigationTarget("/dashboard/settings", "设置"));
        keywordRoutes.put("help", new NavigationTarget("/help", "帮助中心"));
        keywordRoutes.put("帮助", new NavigationTarget("/help", "帮助中心"));
    }

    public NavigationTarget resolve(String userText) {
        if (userText == null || userText.isBlank()) {
            return null;
        }
        String lowered = userText.toLowerCase(Locale.ROOT);
        if (!(lowered.contains("带我去") || lowered.contains("打开") || lowered.contains("go to") || lowered.contains("navigate"))) {
            return null;
        }
        for (var entry : keywordRoutes.entrySet()) {
            if (lowered.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }
}

