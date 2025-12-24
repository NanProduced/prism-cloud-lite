package nan.produced.prism.core.media.application.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.media.application.service.BetterUploadConstant;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaFolderIdUtils {

    /**
     * 规范化 folderId：
     * - null/blank => null
     * - "default" => null（前端根目录占位）
     * - 其他 => trim 后返回
     */
    public static String normalizeFolderId(String folderId) {
        if (!StringUtils.hasText(folderId)) {
            return null;
        }
        String trimmed = folderId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (BetterUploadConstant.DEFAULT_FOLDER_ID.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}

