package nan.produced.prism.core.user.api;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 根据 MIME 类型推断存储文件类型。
 */
public final class StorageFileTypeResolver {

    private StorageFileTypeResolver() {
    }

    public static StorageFileType fromMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return StorageFileType.OTHER;
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/")) {
            return StorageFileType.IMAGE;
        }
        if (lower.startsWith("video/")) {
            return StorageFileType.VIDEO;
        }
        if (lower.startsWith("audio/")) {
            return StorageFileType.AUDIO;
        }
        if (lower.startsWith("application/pdf")
                || lower.startsWith("application/msword")
                || lower.startsWith("application/vnd.")) {
            return StorageFileType.DOCUMENT;
        }
        return StorageFileType.OTHER;
    }
}
