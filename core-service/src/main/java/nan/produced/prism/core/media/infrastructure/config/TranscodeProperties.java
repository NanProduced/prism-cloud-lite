package nan.produced.prism.core.media.infrastructure.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 素材转码配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.media.transcode")
public class TranscodeProperties {

    /**
     * 临时目录（用于下载源文件与输出转码文件）
     */
    private String tempDir = "./tmp/transcode";

    /**
     * ffmpeg 可执行文件（在 PATH 中可直接用 ffmpeg）
     */
    private String ffmpeg = "ffmpeg";

    /**
     * ffprobe 可执行文件（用于获取 duration/width/height 等元数据）
     */
    private String ffprobe = "ffprobe";

    /**
     * 单任务最大执行时长（秒）
     */
    private long maxTaskSeconds = 3 * 60 * 60;

    /**
     * 进度推送最小间隔（毫秒）
     */
    private long progressPublishIntervalMs = 1500;

    /**
     * ffmpeg stderr 日志尾部保留行数（用于失败排查）
     */
    private int stderrTailLines = 200;

    /**
     * 下载连接超时（秒）
     */
    private int downloadConnectTimeoutSeconds = 15;

    /**
     * 下载整体超时（秒，0 表示不设置）
     */
    private int downloadTimeoutSeconds = 0;

    /**
     * 转码失败时是否保留临时文件（便于排查）
     */
    private boolean keepTempOnFailure = false;

    /**
     * 转码 worker 并发（默认串行执行：1）
     */
    private String workerConcurrency = "1";

    /**
     * 转码预设配置（可扩展/覆盖）。key 为 presetId
     */
    private Map<String, Preset> presets = defaultPresets();

    public Preset resolvePreset(String presetId) {
        if (!StringUtils.hasText(presetId)) {
            return null;
        }
        String id = presetId.trim();
        if (id.isEmpty()) {
            return null;
        }

        Preset exact = presets.get(id);
        if (exact != null) {
            return exact;
        }

        String lower = id.toLowerCase(Locale.ROOT);
        Preset lowerMatch = presets.get(lower);
        if (lowerMatch != null) {
            return lowerMatch;
        }

        for (var entry : presets.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(id)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Getter
    @Setter
    public static class Preset {

        /**
         * 预设展示名称（用于 UI 展示）
         */
        private String label;

        /**
         * 目标最大宽度（保持等比缩放），null 表示不缩放
         */
        private Integer width;

        /**
         * 目标最大高度（保持等比缩放），null 表示不缩放
         */
        private Integer height;

        /**
         * CRF（恒定质量，越小越清晰；常用范围 18~28）
         */
        private Integer crf = 23;

        /**
         * 视频编码器（例如：libx264、libx265）
         */
        private String videoCodec = "libx264";

        /**
         * 编码预设（例如：ultrafast/superfast/veryfast/faster/fast/medium/slow/slower/veryslow）
         */
        private String encoderPreset = "medium";

        /**
         * 视频码率（kbps，null 表示不指定，交由 crf 控制）
         */
        private Integer videoBitrateKbps;

        /**
         * 音频编码器（例如：aac）
         */
        private String audioCodec = "aac";

        /**
         * 音频码率（kbps）
         */
        private Integer audioBitrateKbps = 128;

        /**
         * 输出扩展名（例如：mp4）
         */
        private String outputExt = "mp4";

        /**
         * 输出 Content-Type（例如：video/mp4）
         */
        private String outputContentType = "video/mp4";

        /**
         * 是否启用 faststart（将 moov atom 前移，便于在线播放）
         */
        private boolean faststart = true;
    }

    private static Map<String, Preset> defaultPresets() {
        Map<String, Preset> map = new HashMap<>();

        map.put("mp4_360p_h264", preset("360p", 640, 360));
        map.put("mp4_480p_h264", preset("480p", 854, 480));
        map.put("mp4_720p_h264", preset("720p", 1280, 720));
        map.put("mp4_1080p_h264", preset("1080p", 1920, 1080));

        Preset same = preset("same", null, null);
        map.put("mp4_h264", same);

        return map;
    }

    private static Preset preset(String label, Integer width, Integer height) {
        Preset preset = new Preset();
        preset.setLabel(label);
        preset.setWidth(width);
        preset.setHeight(height);
        return preset;
    }
}
