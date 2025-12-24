package nan.produced.prism.core.media.application.util;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.common.util.ObjectKeyUtils;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaLibraryObjectKeyUtils {

    /**
     * mediaLibrary/files 下的内容寻址文件名：
     * <p>
     * F_{MD5_UPPER}_{size}.{ext}
     */
    public static String buildMediaLibraryFilesObjectKey(UploadRouteProperties.RouteConfig routeConfig,
                                                        String filesDir,
                                                        String md5,
                                                        long sizeBytes,
                                                        String ext) {
        if (routeConfig == null || !StringUtils.hasText(routeConfig.getPathPrefix())) {
            throw new IllegalArgumentException("routeConfig/pathPrefix is required");
        }
        if (!StringUtils.hasText(filesDir)) {
            throw new IllegalArgumentException("filesDir is required");
        }
        if (!StringUtils.hasText(md5) || sizeBytes <= 0) {
            throw new IllegalArgumentException("md5/sizeBytes is invalid");
        }

        String md5Upper = md5.trim().toUpperCase(Locale.ROOT);
        String safeExt = StringUtils.hasText(ext) ? ext.trim() : "";
        String filename = "F_" + md5Upper + "_" + sizeBytes + (safeExt.isEmpty() ? "" : "." + safeExt);
        return ObjectKeyUtils.join(routeConfig.getPathPrefix(), filesDir, filename);
    }
}

