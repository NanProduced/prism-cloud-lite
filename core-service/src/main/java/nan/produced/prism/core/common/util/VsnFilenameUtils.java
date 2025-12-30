package nan.produced.prism.core.common.util;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * VSN 文件名解析工具。
 *
 * <p>约定格式：{deviceTitleSnapshot}_{vsnMd5}_{vsnSizeBytes}.vsn</p>
 *
 * <p>示例：New Program-v3_6cfe3f464a34bb56756bdc1085dc9a0d_984.vsn</p>
 */
public final class VsnFilenameUtils {

    private VsnFilenameUtils() {
    }

    public record VsnMeta(String vsnMd5, long vsnSizeBytes) {
    }

    /**
     * 从 VSN 文件名中解析出 md5 与 sizeBytes（无法解析返回 null）。
     *
     * <p>注意：不会解析“节目名/版本号”，仅依赖最后两个 '_' 分隔段。</p>
     */
    public static VsnMeta parseVsnMeta(String vsnFilename) {
        if (!StringUtils.hasText(vsnFilename)) {
            return null;
        }

        String s = vsnFilename.trim();
        int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash + 1 < s.length()) {
            s = s.substring(lastSlash + 1);
        }

        if (s.regionMatches(true, Math.max(0, s.length() - 4), ".vsn", 0, 4)) {
            s = s.substring(0, s.length() - 4);
        }

        int lastUnderscore = s.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= s.length() - 1) {
            return null;
        }

        int secondLastUnderscore = s.lastIndexOf('_', lastUnderscore - 1);
        if (secondLastUnderscore <= 0 || secondLastUnderscore >= lastUnderscore - 1) {
            return null;
        }

        String md5 = s.substring(secondLastUnderscore + 1, lastUnderscore).trim();
        String sizeStr = s.substring(lastUnderscore + 1).trim();
        if (!md5.matches("(?i)[0-9a-f]{32}")) {
            return null;
        }

        long sizeBytes;
        try {
            sizeBytes = Long.parseLong(sizeStr);
        } catch (NumberFormatException e) {
            return null;
        }

        return new VsnMeta(md5.toLowerCase(Locale.ROOT), sizeBytes);
    }
}

