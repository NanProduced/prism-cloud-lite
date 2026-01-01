package nan.produced.prism.core.export.api;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum ExportType {
    DEVICE_LOGS,
    COMMAND_LOGS,
    DEVICE_ONLINE_SESSIONS,
    PROGRAM_PLAY_SESSIONS,
    MEDIA_PLAY_SESSIONS;

    public static ExportType parseOrThrow(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("exportType is blank");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return ExportType.valueOf(upper);
    }
}

