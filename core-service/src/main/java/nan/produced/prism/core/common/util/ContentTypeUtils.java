package nan.produced.prism.core.common.util;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Content-Type 相关工具方法。
 */
public final class ContentTypeUtils {

    private ContentTypeUtils() {
    }

    /**
     * 规范化 Content-Type：trim + lowerCase；为空则返回 defaultValue。
     */
    public static String normalize(String contentType, String defaultValue) {
        if (!StringUtils.hasText(contentType)) {
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
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);

        // image/*
        if (ct.contains("png")) return "png";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("bmp")) return "bmp";
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";

        // video/*
        if (ct.contains("mp4")) return "mp4";
        if (ct.contains("quicktime")) return "mov";
        if (ct.startsWith("video/") && ct.contains("mpeg")) return "mpg";

        // audio/*
        if (ct.contains("audio/mpeg")) return "mp3";
        if (ct.contains("audio/wav") || ct.contains("audio/x-wav")) return "wav";

        // document/text
        if (ct.contains("pdf")) return "pdf";
        if (ct.startsWith("text/")) return "txt";

        return null;
    }

    public static String guessExtensionOrDefault(String contentType, String defaultExt) {
        String ext = guessExtension(contentType);
        return StringUtils.hasText(ext) ? ext : defaultExt;
    }
}

