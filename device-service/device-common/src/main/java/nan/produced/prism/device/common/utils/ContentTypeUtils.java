package nan.produced.prism.device.common.utils;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Content-Type 工具方法（device-service 侧通用）。
 */
public final class ContentTypeUtils {

    private ContentTypeUtils() {
    }

    /**
     * 规范化 Content-Type：trim + lowerCase；为空则返回 defaultValue。
     */
    public static String normalize(String contentType, String defaultValue) {
        if (StringUtils.isBlank(contentType)) {
            return defaultValue;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    /**
     * 根据 Content-Type 猜测常见文件扩展名（不含点）。
     *
     * @return 识别成功返回扩展名；无法识别返回 null
     */
    public static String guessExtension(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);

        if (ct.contains("png")) return "png";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("bmp")) return "bmp";
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";

        return null;
    }

    public static String guessExtensionOrDefault(String contentType, String defaultExt) {
        String ext = guessExtension(contentType);
        return StringUtils.isNotBlank(ext) ? ext : defaultExt;
    }
}
