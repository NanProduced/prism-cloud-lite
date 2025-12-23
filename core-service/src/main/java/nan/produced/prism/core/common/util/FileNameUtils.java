package nan.produced.prism.core.common.util;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 文件名/扩展名工具方法（面向对象存储 key 与业务文件名）。
 */
public final class FileNameUtils {

    private FileNameUtils() {
    }

    /**
     * 获取路径/文件名的扩展名（不含点，返回小写）。
     *
     * @return 无扩展名则返回 null
     */
    public static String tryGetExtension(String filenameOrPath) {
        if (!StringUtils.hasText(filenameOrPath)) {
            return null;
        }
        String normalized = filenameOrPath.trim().replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0 || lastDot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(lastDot + 1).trim().toLowerCase(Locale.ROOT);
        return ext.isEmpty() ? null : ext;
    }

    public static String getExtensionOrDefault(String filenameOrPath, String defaultExt) {
        String ext = tryGetExtension(filenameOrPath);
        return StringUtils.hasText(ext) ? ext : defaultExt;
    }

    /**
     * 去掉最后一个扩展名（仅针对最后一个 path segment）。
     */
    public static String baseName(String filenameOrPath) {
        if (!StringUtils.hasText(filenameOrPath)) {
            return "";
        }
        String normalized = filenameOrPath.trim().replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return name;
        }
        return name.substring(0, lastDot);
    }

    /**
     * 优先根据 Content-Type 推断扩展名；否则从 fileName 中解析；最终返回空字符串表示未知。
     */
    public static String resolveExtensionOrEmpty(String fileName, String contentType) {
        String ext = ContentTypeUtils.guessExtension(contentType);
        if (StringUtils.hasText(ext)) {
            return ext;
        }
        String byName = tryGetExtension(fileName);
        return byName == null ? "" : byName;
    }

    /**
     * 优先根据 Content-Type 推断扩展名；否则从 fileName/key 中解析；最终返回 defaultExt。
     */
    public static String resolveExtensionOrDefault(String fileNameOrKey, String contentType, String defaultExt) {
        String ext = ContentTypeUtils.guessExtension(contentType);
        if (StringUtils.hasText(ext)) {
            return ext;
        }
        String byName = tryGetExtension(fileNameOrKey);
        return StringUtils.hasText(byName) ? byName : defaultExt;
    }

    /**
     * 将文件名转换为可用于路径/URL 的 slug（基于文件 baseName）。
     * <p>若生成结果为空，使用当前时间戳兜底。</p>
     */
    public static String slugifyFileName(String fileName, int maxLength) {
        String base = baseName(fileName);
        String slug = TextSlugifier.toSlug(base, maxLength);
        return StringUtils.hasText(slug) ? slug : String.valueOf(System.currentTimeMillis());
    }
}
