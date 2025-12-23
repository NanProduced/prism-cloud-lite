package nan.produced.prism.core.common.util;

import org.springframework.util.StringUtils;

/**
 * 对象存储 key 拼接工具（统一使用 '/' 分隔）。
 */
public final class ObjectKeyUtils {

    private ObjectKeyUtils() {
    }

    public static String join(String... parts) {
        if (parts == null || parts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : parts) {
            if (StringUtils.hasText(raw)) {
                String p = raw.trim().replace('\\', '/');
                p = p.replaceAll("^/+", "").replaceAll("/+$", "");
                if (!p.isEmpty()) {
                    if (!sb.isEmpty()) {
                        sb.append('/');
                    }
                    sb.append(p);
                }
            }
        }
        return sb.toString();
    }
}